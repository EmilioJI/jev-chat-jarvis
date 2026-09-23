package com.jev.probe.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jev.probe.core.Prefs
import java.util.concurrent.ConcurrentHashMap

/**
 * Standard Android notification-listener integration for WeChat.
 *
 * This service never opens WeChat, never reads its view tree and never performs
 * UI actions. It only receives notification payloads after the user explicitly
 * grants Android notification access.
 */
class WeChatNotificationListenerService : NotificationListenerService() {

    private lateinit var prefs: Prefs
    private val recent = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        prefs = Prefs(this)
        if (prefs.enabled) KeepAliveService.start(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName != WECHAT_PKG) return
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!prefs.enabled) return

        val notification = n.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        ).mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val text = if (textLines.isNotEmpty()) {
            textLines.joinToString("\n")
        } else {
            sequenceOf(
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                extras.getCharSequence(Notification.EXTRA_TEXT)
            ).mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

        if (title.isBlank() && text.isBlank()) return

        val signature = title + "\u0000" + text
        val now = System.currentTimeMillis()
        val last = recent[signature]
        if (last != null && now - last < DEDUPE_MS) return
        recent[signature] = now
        if (recent.size > 80) {
            val cutoff = now - 10 * 60_000L
            recent.entries.removeIf { it.value < cutoff }
        }

        val userLabel = n.user?.toString().orEmpty().ifBlank { "default" }
        KeepAliveService.pushWeChatNotification(
            this,
            title = title.take(MAX_TITLE_CHARS),
            text = text.take(MAX_TEXT_CHARS),
            postTime = n.postTime.coerceAtLeast(1L),
            userLabel = userLabel
        )
    }

    companion object {
        private const val WECHAT_PKG = "com.tencent.mm"
        private const val DEDUPE_MS = 2500L
        private const val MAX_TITLE_CHARS = 120
        private const val MAX_TEXT_CHARS = 4000
    }
}
