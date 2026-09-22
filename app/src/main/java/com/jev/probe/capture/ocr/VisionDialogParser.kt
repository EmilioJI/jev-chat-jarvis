package com.jev.probe.capture.ocr

import com.jev.probe.core.Msg

/**
 * Strict parser for the remote vision OCR contract.
 *
 * Only explicitly labelled speaker lines are accepted. Unlabelled prose,
 * markdown fences and explanations are ignored instead of being guessed into
 * the conversation with the wrong side.
 */
object VisionDialogParser {

    private val linePattern = Regex(
        """^(?:[-*]\\s*)?(我|自己|me|Me|ME|对方|对面|other|Other|OTHER)\\s*[:：]\\s*(.+)$"""
    )

    fun parse(raw: String): List<Msg> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
            .mapNotNull { line ->
                val m = linePattern.matchEntire(line) ?: return@mapNotNull null
                val who = m.groupValues[1].lowercase()
                val text = m.groupValues[2].trim()
                if (text.isBlank()) return@mapNotNull null
                val side = when (who) {
                    "我", "自己", "me" -> "me"
                    else -> "other"
                }
                Msg(side, text)
            }
            .toList()
    }

    /** Pure deterministic smoke check used by the on-device settings self-test. */
    fun selfCheck(): Boolean {
        val sample = """
            我：收到，我今晚处理
            对方: 好，别忘了
            这行没有说话人标签，必须丢弃
            - Me: 明白
            * Other：行
        """.trimIndent()
        val got = parse(sample)
        return got == listOf(
            Msg("me", "收到，我今晚处理"),
            Msg("other", "好，别忘了"),
            Msg("me", "明白"),
            Msg("other", "行")
        )
    }
}
