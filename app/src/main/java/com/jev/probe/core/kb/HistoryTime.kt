package com.jev.probe.core.kb

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object HistoryTime {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    const val SEMANTICS =
        "“记录于”表示本助手保存/看到该历史的时间，不保证等于聊天软件原始发送时间。"

    const val FRESHNESS_RULE =
        "判断时效时优先使用更近记录和当前对话；旧记录中的临时计划、日期、地点、状态、截止时间或承诺可能已过期，" +
            "除非后来再次确认，不要把它们当成当前事实。稳定身份、长期偏好等可作为背景，但若与新记录冲突，以新记录为准。"

    fun absolute(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (ts <= 0L) return "时间未知"
        return formatter.format(Instant.ofEpochMilli(ts).atZone(zone))
    }

    fun age(ts: Long, now: Long = System.currentTimeMillis()): String {
        if (ts <= 0L) return "时间未知"
        if (ts > now + 60_000L) return "时间异常"
        val ms = max(0L, now - ts)
        val minute = 60_000L
        val hour = 60L * minute
        val day = 24L * hour
        return when {
            ms < minute -> "刚刚"
            ms < hour -> "约${ms / minute}分钟前"
            ms < day -> "约${ms / hour}小时前"
            ms < 7L * day -> "约${ms / day}天前"
            ms < 30L * day -> "约${ms / (7L * day)}周前"
            ms < 365L * day -> "约${ms / (30L * day)}个月前"
            else -> "约${ms / (365L * day)}年前"
        }
    }

    fun stamp(
        ts: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String = "[记录于 ${absolute(ts, zone)}｜${age(ts, now)}]"

    fun current(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String = "当前设备时间：${absolute(now, zone)}（${zone.id}）"

    fun modelGuidance(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String = current(now, zone) + "。\n" + SEMANTICS + "\n" + FRESHNESS_RULE
}
