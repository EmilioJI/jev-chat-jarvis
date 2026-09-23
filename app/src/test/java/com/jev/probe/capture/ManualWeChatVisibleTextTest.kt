package com.jev.probe.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ManualWeChatVisibleTextTest {

    @Test
    fun extractsTitleAndLeftRightMessagesWithoutWechatIds() {
        val items = listOf(
            ManualWeChatVisibleText.Item("张三", 500, 120, 900, 180),
            ManualWeChatVisibleText.Item("12:30", 620, 520, 820, 570),
            ManualWeChatVisibleText.Item("你好，今天还开会吗", 100, 900, 720, 1020),
            ManualWeChatVisibleText.Item("下午三点可以", 760, 1200, 1360, 1320),
            ManualWeChatVisibleText.Item("发送", 1180, 2700, 1390, 2800)
        )

        val snapshot = ManualWeChatVisibleText.fromVisibleItems(
            items,
            width = 1440,
            height = 3200
        )

        assertNotNull(snapshot)
        assertEquals("张三", snapshot!!.title)
        assertEquals(2, snapshot.messages.size)
        assertEquals("other", snapshot.messages[0].side)
        assertEquals("你好，今天还开会吗", snapshot.messages[0].text)
        assertEquals("me", snapshot.messages[1].side)
        assertEquals("下午三点可以", snapshot.messages[1].text)
    }

    @Test
    fun emptyMessageAreaReturnsExplicitEmptySnapshot() {
        val items = listOf(
            ManualWeChatVisibleText.Item("张三", 500, 120, 900, 180),
            ManualWeChatVisibleText.Item("昨天", 600, 800, 840, 850),
            ManualWeChatVisibleText.Item("发送", 1180, 2700, 1390, 2800)
        )

        val snapshot = ManualWeChatVisibleText.fromVisibleItems(
            items,
            width = 1440,
            height = 3200
        )

        assertNotNull(snapshot)
        assertEquals("张三", snapshot!!.title)
        assertEquals(0, snapshot.messages.size)
    }
}
