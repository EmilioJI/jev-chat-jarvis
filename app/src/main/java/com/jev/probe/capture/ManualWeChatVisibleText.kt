package com.jev.probe.capture

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg

/**
 * User-triggered, generic visible-text reader for normal-user WeChat.
 *
 * No WeChat resource ids are referenced here. No event subscription is needed.
 * The caller invokes this only after the user taps "分析当前对话".
 *
 * If WeChat does not expose message text to a normal AccessibilityService, this
 * returns null/empty and callers fall back to notification / clipboard / local
 * OCR. We deliberately do not attempt to bypass that restriction.
 */
internal object ManualWeChatVisibleText {

    internal data class Item(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
    }

    fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot? {
        val width = res.displayMetrics.widthPixels
        val height = res.displayMetrics.heightPixels
        if (width <= 0 || height <= 0) return null

        val items = ArrayList<Item>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard < 7000) {
            guard++
            val node = stack.removeLast()
            val text = node.text?.toString()?.trim()
            val cls = node.className?.toString().orEmpty()
            if (!text.isNullOrBlank() && cls.endsWith("TextView")) {
                val b = Rect()
                node.getBoundsInScreen(b)
                if (b.width() > 0 && b.height() > 0) {
                    items.add(Item(text, b.left, b.top, b.right, b.bottom))
                }
            }
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return fromVisibleItems(items, width, height)
    }

    internal fun fromVisibleItems(
        source: List<Item>,
        width: Int,
        height: Int
    ): ChatSnapshot? {
        if (source.isEmpty() || width <= 0 || height <= 0) return null

        val topBand = (height * 0.14).toInt()
        val bottomBand = (height * 0.82).toInt()

        val title = source.asSequence()
            .filter { it.bottom in 1..topBand }
            .filter { it.centerX in (width * 0.20).toInt()..(width * 0.80).toInt() }
            .map { it.text.trim() }
            .filter { it.length in 1..30 }
            .filterNot(::isTimestampLike)
            .filterNot(::isChrome)
            .firstOrNull()

        val seen = HashSet<String>()
        val body = source.asSequence()
            .filter { it.top > topBand && it.bottom < bottomBand }
            .filter { it.text.length in 1..600 }
            .filterNot { isTimestampLike(it.text) || isChrome(it.text) }
            .filter {
                // Collapse nested TextViews that render the same string at nearly
                // the same vertical position.
                seen.add(it.text + "\u0000" + (it.top / 8))
            }
            .sortedBy { it.top }
            .takeLast(20)
            .toList()

        if (body.isEmpty()) return ChatSnapshot(
            title = title,
            messages = emptyList(),
            note = "微信标准可见文本读取 · 当前未暴露消息正文"
        )

        val messages = body.map { item ->
            val leftGap = item.left.coerceAtLeast(0)
            val rightGap = (width - item.right).coerceAtLeast(0)
            val side = when {
                rightGap + width * 0.06 < leftGap -> "me"
                leftGap + width * 0.06 < rightGap -> "other"
                item.centerX > width / 2 -> "me"
                else -> "other"
            }
            Msg(side, item.text)
        }

        return ChatSnapshot(
            title = title,
            messages = messages,
            note = "微信标准可见文本 · 由你主动触发；说话人按左右位置估计"
        )
    }

    private fun isTimestampLike(raw: String): Boolean {
        val t = raw.trim()
        if (t in setOf("今天", "昨天", "前天")) return true
        if (Regex("""^(星期|周)[一二三四五六日天]$""").matches(t)) return true
        if (Regex("""^(上午|下午|晚上)?\s*\d{1,2}[:：]\d{2}$""").matches(t)) return true
        if (Regex("""^\d{1,2}月\d{1,2}日(\s+\d{1,2}[:：]\d{2})?$""").matches(t)) return true
        return false
    }

    private fun isChrome(raw: String): Boolean {
        val t = raw.trim()
        return t in setOf(
            "微信", "通讯录", "发现", "我", "发送", "按住 说话",
            "按住说话", "表情", "更多功能", "视频通话", "语音通话"
        )
    }
}
