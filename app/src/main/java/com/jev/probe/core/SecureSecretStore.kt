package com.jev.probe.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore-backed value encryption for API credentials.
 *
 * Ciphertext lives in ordinary app-private SharedPreferences; the AES key never
 * leaves AndroidKeyStore. This deliberately avoids deprecated
 * EncryptedSharedPreferences / MasterKey APIs.
 *
 * Migration is fail-safe: plaintext is encrypted, immediately decrypted and
 * compared, and only after a verified successful commit is plaintext removed.
 */
internal class SecureSecretStore(
    context: Context,
    private val prefs: SharedPreferences
) {

    private val alias = context.packageName + ".jev.api-secrets.v1"

    fun readOrMigrate(encryptedPref: String, legacyPlainPref: String): String {
        val encrypted = prefs.getString(encryptedPref, null)
        if (!encrypted.isNullOrBlank()) {
            try {
                val value = decrypt(encrypted)
                // A prior phase-2 cleanup may have failed; retry opportunistically.
                if (prefs.contains(legacyPlainPref))
                    prefs.edit().remove(legacyPlainPref).commit()
                return value
            } catch (e: Exception) {
                Log.w(TAG, "secret decrypt failed: ${e.javaClass.simpleName}")
            }
        }

        val legacy = prefs.getString(legacyPlainPref, "") ?: ""
        if (legacy.isBlank()) return ""

        return try {
            val encoded = encrypt(legacy)
            check(decrypt(encoded) == legacy) { "secret round-trip mismatch" }
            // Phase 1: persist verified ciphertext while plaintext still
            // exists. Only after this succeeds may phase 2 remove plaintext.
            val encryptedCommitted = prefs.edit()
                .putString(encryptedPref, encoded)
                .commit()
            if (encryptedCommitted) {
                prefs.edit().remove(legacyPlainPref).commit()
                Log.i(TAG, "secret migrated to AndroidKeyStore")
            }
            legacy
        } catch (e: Exception) {
            Log.w(TAG, "secret migration deferred: ${e.javaClass.simpleName}")
            legacy
        }
    }

    fun write(encryptedPref: String, legacyPlainPref: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(encryptedPref).remove(legacyPlainPref).apply()
            return
        }

        try {
            val encoded = encrypt(value)
            check(decrypt(encoded) == value) { "secret round-trip mismatch" }
            val encryptedCommitted = prefs.edit()
                .putString(encryptedPref, encoded)
                .commit()
            if (encryptedCommitted) {
                // Failure here only leaves both copies; the next read prefers
                // verified ciphertext and can retry cleanup later.
                prefs.edit().remove(legacyPlainPref).commit()
            } else {
                prefs.edit().putString(legacyPlainPref, value).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "secure secret write degraded: ${e.javaClass.simpleName}")
            prefs.edit()
                .remove(encryptedPref)
                .putString(legacyPlainPref, value)
                .apply()
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return VERSION + "." +
            encoder.encodeToString(cipher.iv) + "." +
            encoder.encodeToString(ciphertext)
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split('.')
        require(parts.size == 3 && parts[0] == VERSION) { "unsupported secret format" }
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(parts[1])
        val ciphertext = decoder.decode(parts[2])
        require(iv.size == GCM_IV_BYTES) { "invalid GCM IV" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "JEVASSIST"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = "v1"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }
}
