package com.jev.probe.jev

import com.jev.probe.core.Analysis
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.RankedReply
import com.jev.probe.core.kb.ChatContext

/**
 * Pluggable judgment/ranking boundary.
 *
 * Jev and structured LLMs expose different wire protocols; everything above
 * this interface stays provider-agnostic.
 */
interface JudgeEngine {
    fun judge(
        snapshot: ChatSnapshot,
        relationship: String,
        ctx: ChatContext? = null
    ): Analysis

    fun rank(
        snapshot: ChatSnapshot,
        relationship: String,
        candidates: List<String>,
        ctx: ChatContext? = null
    ): List<RankedReply>
}
