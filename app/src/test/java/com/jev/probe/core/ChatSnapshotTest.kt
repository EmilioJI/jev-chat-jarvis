package com.jev.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSnapshotTest {

    @Test
    fun signatureIncludesConversationTitle() {
        val messages = listOf(
            Msg("other", "同一条消息"),
            Msg("me", "同一条回复")
        )
        val a = ChatSnapshot("张三", messages)
        val b = ChatSnapshot("李四", messages)

        assertNotEquals(a.signature(), b.signature())
    }

    @Test
    fun signatureTracksRecentMessageSideAndText() {
        val base = ChatSnapshot(
            "会话",
            listOf(Msg("other", "你好"), Msg("me", "收到"))
        )
        val changedText = ChatSnapshot(
            "会话",
            listOf(Msg("other", "你好"), Msg("me", "稍后回复"))
        )
        val changedSide = ChatSnapshot(
            "会话",
            listOf(Msg("other", "你好"), Msg("other", "收到"))
        )

        assertNotEquals(base.signature(), changedText.signature())
        assertNotEquals(base.signature(), changedSide.signature())
    }

    @Test
    fun signatureIntentionallyUsesOnlyLastSixMessages() {
        val tail = (1..6).map { Msg(if (it % 2 == 0) "me" else "other", "尾部-$it") }
        val a = ChatSnapshot("会话", listOf(Msg("other", "很早以前-A")) + tail)
        val b = ChatSnapshot("会话", listOf(Msg("other", "很早以前-B")) + tail)

        assertEquals(a.signature(), b.signature())
    }

    @Test
    fun latestFromFollowsLastVisibleMessage() {
        assertNull(ChatSnapshot("空", emptyList()).latestFrom)
        assertEquals(
            "other",
            ChatSnapshot("会话", listOf(Msg("me", "1"), Msg("other", "2"))).latestFrom
        )
        assertTrue(ChatSnapshot(null, listOf(Msg("me", "x"))).signature().isNotBlank())
    }
}
