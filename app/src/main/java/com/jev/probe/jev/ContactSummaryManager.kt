package com.jev.probe.jev

import com.jev.probe.core.Prefs
import com.jev.probe.core.kb.Contact
import com.jev.probe.core.kb.HistoryTime
import com.jev.probe.core.kb.KbStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-frequency rolling contact summary.
 *
 * This is opt-in and intentionally off the critical path: it runs only after a
 * normal analysis round has already produced its reply candidates. Failures do
 * not affect judgment/reply UI.
 */
object ContactSummaryManager {

    private const val FIRST_SUMMARY_MIN_LINES = 24
    private const val RESUMMARY_NEW_LINES = 20
    private const val MAX_INPUT_LINES = 80
    private const val MAX_INPUT_CHARS = 6000
    private const val MAX_SUMMARY_CHARS = 240
    private const val RETRY_COOLDOWN_MS = 30L * 60L * 1000L

    private val lastAttemptAt = ConcurrentHashMap<String, Long>()

    /**
     * @return true only when a new summary was successfully persisted.
     */
    fun maybeRefresh(
        store: KbStore,
        contact: Contact,
        prefs: Prefs
    ): Boolean {
        if (!prefs.contextEnabled || !prefs.autoSummary) return false
        if (prefs.effectiveReplyKey().isBlank()) return false

        val log = store.recentLog(contact.id, KbStore.MAX_LOG)
        if (log.isEmpty()) return false

        val through = contact.autoSummaryThroughTs.coerceAtLeast(0L)
        val newLines = if (through == 0L) {
            log.size
        } else {
            log.count { it.ts > through }
        }
        val needed = if (through == 0L) FIRST_SUMMARY_MIN_LINES else RESUMMARY_NEW_LINES
        if (newLines < needed) return false

        val now = System.currentTimeMillis()
        val lastAttempt = lastAttemptAt[contact.id] ?: 0L
        if (now - lastAttempt < RETRY_COOLDOWN_MS) return false
        lastAttemptAt[contact.id] = now

        val nowForSummary = System.currentTimeMillis()
        val selected = ArrayList<String>()
        var chars = 0
        for (entry in log.takeLast(MAX_INPUT_LINES).asReversed()) {
            val line = HistoryTime.stamp(entry.ts, nowForSummary) + " " +
                (if (entry.side == "me") "我：" else "对方：") + entry.text.trim()
            if (line.isBlank()) continue
            if (selected.isNotEmpty() && chars + line.length + 1 > MAX_INPUT_CHARS) break
            selected.add(line)
            chars += line.length + 1
        }
        selected.reverse()
        if (selected.isEmpty()) return false

        val payload = buildString {
            if (contact.autoSummary.isNotBlank()) {
                append("已有摘要（仅作旧背景，需根据新聊天修正）：\n")
                append(contact.autoSummary.trim()).append("\n\n")
            }
            append(HistoryTime.modelGuidance(nowForSummary)).append("\n\n")
            append("最近聊天记录（时间为本助手记录时间）：\n")
            selected.forEach { append(it).append('\n') }
        }

        val summary = try {
            ReplyClient(prefs).summarize(payload).trim().take(MAX_SUMMARY_CHARS)
        } catch (_: Exception) {
            return false
        }
        if (summary.isBlank()) return false

        // Re-read before writing so a concurrent manual contact edit is preserved.
        val fresh = store.contact(contact.id) ?: return false
        val newestTs = log.maxOfOrNull { it.ts } ?: 0L
        return store.saveContact(
            fresh.copy(
                autoSummary = summary,
                autoSummaryThroughTs = newestTs.coerceAtLeast(1L)
            ),
            touchUpdatedAt = false
        )
    }
}
