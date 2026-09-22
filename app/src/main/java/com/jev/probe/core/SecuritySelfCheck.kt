package com.jev.probe.core

import android.content.Context

/**
 * On-device AndroidKeyStore smoke test using a harmless dummy value.
 *
 * It does not read or modify any real API credential and performs no network
 * access. The same app-scoped Keystore AES key is exercised against a temporary
 * SharedPreferences file, which is deleted immediately afterwards.
 */
object SecuritySelfCheck {

    private const val PREFS = "jev_security_selfcheck_scratch"
    private const val ENC = "probe_enc"
    private const val PLAIN = "probe_plain"
    private const val PROBE = "jev-keystore-selfcheck-value"

    fun run(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        return try {
            val store = SecureSecretStore(context, sp)
            store.write(ENC, PLAIN, PROBE)

            val readBack = store.readOrMigrate(ENC, PLAIN)
            val encrypted = sp.getString(ENC, "").orEmpty()
            val plaintextRemains = sp.contains(PLAIN)

            when {
                readBack != PROBE ->
                    "安全存储自检失败：解密回读不一致"
                encrypted.isBlank() ->
                    "安全存储自检失败：未生成密文"
                encrypted.contains(PROBE) ->
                    "安全存储自检失败：存储值仍含明文"
                plaintextRemains ->
                    "安全存储自检失败：明文未清理"
                else ->
                    "安全存储自检通过：AndroidKeyStore AES-GCM 加解密正常，scratch 无明文残留。"
            }
        } catch (e: Exception) {
            "安全存储自检异常：${e.javaClass.simpleName} ${e.message ?: ""}"
        } finally {
            sp.edit().clear().commit()
        }
    }
}
