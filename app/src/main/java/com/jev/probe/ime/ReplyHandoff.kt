package com.jev.probe.ime

import com.jev.probe.core.RankedReply

/**
 * Ephemeral hand-off from the assistant overlay to the optional IME.
 *
 * Nothing is persisted: if the process dies, candidates disappear. This keeps
 * generated chat replies out of another on-disk store while still letting the
 * IME retrieve them when it runs in the normal app process.
 */
object ReplyHandoff {
    private const val TTL_MS = 10 * 60_000L

    @Volatile private var updatedAt: Long = 0L
    @Volatile private var replies: List<RankedReply> = emptyList()

    @Synchronized
    fun publish(items: List<RankedReply>) {
        replies = items.take(3).map { it.copy() }
        updatedAt = System.currentTimeMillis()
    }

    @Synchronized
    fun current(now: Long = System.currentTimeMillis()): List<RankedReply> {
        if (updatedAt <= 0L || now - updatedAt > TTL_MS) {
            clear()
            return emptyList()
        }
        return replies.toList()
    }

    @Synchronized
    fun clear() {
        replies = emptyList()
        updatedAt = 0L
    }
}
