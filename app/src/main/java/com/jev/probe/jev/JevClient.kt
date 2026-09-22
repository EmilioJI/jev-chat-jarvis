package com.jev.probe.jev

import com.jev.probe.core.Analysis
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import com.jev.probe.core.kb.ChatContext

/**
 * Back-compatible analysis facade.
 *
 * The product name stays Jev, but the judgment engine is now pluggable:
 * Jev Decisions by default, or a structured LLM preset such as GLM-4.7.
 * Reply generation remains a separate route.
 */
class JevClient(prefs: Prefs) {

    private val judgeEngine: JudgeEngine = when (prefs.judgeProvider) {
        Prefs.PROVIDER_GLM47 -> StructuredJudgeClient(prefs)
        else -> JudgeClient(prefs)
    }
    private val replyClient = ReplyClient(prefs)

    /** The 7 judgment questions. Errors come back inside [Analysis.error]. */
    fun judge(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): Analysis =
        judgeEngine.judge(snapshot, relationship, ctx)

    /** Draft 3 candidates on the reply route, then rank them on the judge route. */
    fun draftAndRank(
        snapshot: ChatSnapshot,
        relationship: String,
        ctx: ChatContext? = null
    ): List<RankedReply> {
        val candidates = replyClient.draft(snapshot, relationship, ctx)
        return judgeEngine.rank(snapshot, relationship, candidates, ctx)
    }

    /** Judge + replies, sequential. Used by the settings connectivity test. */
    fun analyze(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): Analysis {
        val a = judge(snapshot, relationship, ctx)
        if (a.error != null) return a
        val ranked = try { draftAndRank(snapshot, relationship, ctx) } catch (e: Exception) { emptyList() }
        return a.copy(rankedReplies = ranked)
    }
}
