package com.jev.probe.capture.ocr

import com.jev.probe.core.Msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionDialogParserTest {

    @Test
    fun builtInSelfCheckPasses() {
        assertTrue(VisionDialogParser.selfCheck())
    }

    @Test
    fun parserAcceptsOnlyExplicitSpeakerLabels() {
        val raw = """
            这是模型的解释，必须忽略
            我：今晚八点到
            对方: 好
            ```text
            没标签的聊天正文也必须忽略
            ```
            - Me: see you
            * OTHER：ok
        """.trimIndent()

        assertEquals(
            listOf(
                Msg("me", "今晚八点到"),
                Msg("other", "好"),
                Msg("me", "see you"),
                Msg("other", "ok")
            ),
            VisionDialogParser.parse(raw)
        )
    }

    @Test
    fun parserSupportsChineseAliasesAndRejectsBlankPayloads() {
        assertEquals(
            listOf(
                Msg("me", "A"),
                Msg("other", "B")
            ),
            VisionDialogParser.parse("自己：A\n对面: B")
        )
        assertTrue(VisionDialogParser.parse("   ").isEmpty())
        assertTrue(VisionDialogParser.parse("我：   ").isEmpty())
    }
}
