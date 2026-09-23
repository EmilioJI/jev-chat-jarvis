package com.jev.probe.ime

import com.jev.probe.core.RankedReply
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyHandoffTest {

    @After
    fun cleanup() {
        ReplyHandoff.clear()
    }

    @Test
    fun publishesAtMostThreeRepliesInMemory() {
        ReplyHandoff.publish(
            listOf(
                RankedReply("A", 0.4),
                RankedReply("B", 0.3),
                RankedReply("C", 0.2),
                RankedReply("D", 0.1)
            )
        )
        assertEquals(listOf("A", "B", "C"), ReplyHandoff.current().map { it.text })
    }

    @Test
    fun armedReplyIsConsumedExactlyOnce() {
        ReplyHandoff.arm("hello")
        assertEquals("hello", ReplyHandoff.consumeArmed())
        assertEquals(null, ReplyHandoff.consumeArmed())
    }

    @Test
    fun staleRepliesExpire() {
        ReplyHandoff.publish(listOf(RankedReply("old", 1.0)))
        assertTrue(ReplyHandoff.current(Long.MAX_VALUE).isEmpty())
    }
}
