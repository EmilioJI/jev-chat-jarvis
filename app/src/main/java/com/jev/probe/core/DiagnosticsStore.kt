package com.jev.probe.core

import android.content.Context
import android.os.SystemClock

/**
 * Privacy-safe capture diagnostics.
 *
 * Stores metadata only: never conversation titles, message text, OCR text,
 * API keys, prompts or model responses.
 */
object DiagnosticsStore {

    data class Snapshot(
        val updatedAt: Long = 0L,
        val packageName: String = "",
        val adapter: String = "",
        val source: String = "",
        val titlePresent: Boolean = false,
        val messageCount: Int = 0,
        val latestFrom: String = "",
        val status: String = ""
    )

    private const val PREFS = "jev_capture_diagnostics"
    private const val K_UPDATED = "updated_at"
    private const val K_PACKAGE = "package_name"
    private const val K_ADAPTER = "adapter"
    private const val K_SOURCE = "source"
    private const val K_TITLE_PRESENT = "title_present"
    private const val K_MESSAGE_COUNT = "message_count"
    private const val K_LATEST_FROM = "latest_from"
    private const val K_STATUS = "status"

    @Volatile private var lastFingerprint = ""
    @Volatile private var lastWriteElapsed = 0L

    @Synchronized
    fun record(
        context: Context,
        packageName: String,
        adapter: String,
        source: String,
        titlePresent: Boolean,
        messageCount: Int,
        latestFrom: String?,
        status: String
    ) {
        val safeLatest = when (latestFrom) {
            "me", "other" -> latestFrom
            else -> ""
        }
        val safeCount = messageCount.coerceAtLeast(0)
        val fingerprint = listOf(
            packageName,
            adapter,
            source,
            titlePresent.toString(),
            safeCount.toString(),
            safeLatest,
            status
        ).joinToString("|")

        val elapsed = SystemClock.elapsedRealtime()
        if (fingerprint == lastFingerprint && elapsed - lastWriteElapsed < 2_000L) return
        lastFingerprint = fingerprint
        lastWriteElapsed = elapsed

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(K_UPDATED, System.currentTimeMillis())
            .putString(K_PACKAGE, packageName.take(120))
            .putString(K_ADAPTER, adapter.take(40))
            .putString(K_SOURCE, source.take(40))
            .putBoolean(K_TITLE_PRESENT, titlePresent)
            .putInt(K_MESSAGE_COUNT, safeCount.coerceAtMost(500))
            .putString(K_LATEST_FROM, safeLatest)
            .putString(K_STATUS, status.take(80))
            .apply()
    }

    fun read(context: Context): Snapshot {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            updatedAt = sp.getLong(K_UPDATED, 0L),
            packageName = sp.getString(K_PACKAGE, "") ?: "",
            adapter = sp.getString(K_ADAPTER, "") ?: "",
            source = sp.getString(K_SOURCE, "") ?: "",
            titlePresent = sp.getBoolean(K_TITLE_PRESENT, false),
            messageCount = sp.getInt(K_MESSAGE_COUNT, 0).coerceAtLeast(0),
            latestFrom = sp.getString(K_LATEST_FROM, "") ?: "",
            status = sp.getString(K_STATUS, "") ?: ""
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        lastFingerprint = ""
        lastWriteElapsed = 0L
    }
}
