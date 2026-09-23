package com.jev.probe.core.kb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryTimeTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun absoluteUsesRequestedZone() {
        val ts = Instant.parse("2026-09-22T14:00:00Z").toEpochMilli()
        assertEquals("2026-09-22 22:00", HistoryTime.absolute(ts, zone))
    }

    @Test
    fun ageUsesReadableBuckets() {
        val now = Instant.parse("2026-09-22T14:00:00Z").toEpochMilli()
        assertEquals("约5分钟前", HistoryTime.age(now - 5L * 60_000L, now))
        assertEquals("约3小时前", HistoryTime.age(now - 3L * 60L * 60_000L, now))
        assertEquals("约2天前", HistoryTime.age(now - 2L * 24L * 60L * 60_000L, now))
        assertEquals("约2周前", HistoryTime.age(now - 14L * 24L * 60L * 60_000L, now))
    }

    @Test
    fun stampMakesRecordedTimeSemanticsVisible() {
        val now = Instant.parse("2026-09-22T14:00:00Z").toEpochMilli()
        val ts = now - 2L * 24L * 60L * 60_000L
        val stamp = HistoryTime.stamp(ts, now, zone)
        assertEquals("[记录于 2026-09-20 22:00｜约2天前]", stamp)
        assertTrue(HistoryTime.SEMANTICS.contains("不保证等于聊天软件原始发送时间"))
        assertTrue(HistoryTime.FRESHNESS_RULE.contains("以新记录为准"))
    }
}
