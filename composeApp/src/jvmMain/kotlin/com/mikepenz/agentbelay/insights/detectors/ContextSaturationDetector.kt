package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Long sessions whose context creeps past 140k without a `/compact`. Auto-
 * compact triggers around 167k; quality already degrades earlier. The
 * remediation is a focused `/compact` ("preserve open files + current
 * TODO") at the inflection point.
 *
 * We can't see compact commands directly, but a session that crosses 140k
 * input on its latest turn AND has 80+ turns is the canonical case.
 */
class ContextSaturationDetector(
    private val saturationThreshold: Long = 140_000L,
    private val turnThreshold: Int = 80,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        if (session.turnCount < turnThreshold) return emptyList()
        val latest = session.turns.lastOrNull() ?: return emptyList()
        val latestInput = latest.inputTokens + latest.cacheReadTokens + latest.cacheWriteTokens
        if (latestInput < saturationThreshold) return emptyList()

        // First turn whose context crossed 70% of saturation — that's the
        // recommended compact point.
        val pivot = (saturationThreshold * 0.7).toLong()
        val pivotIdx = session.turns.indexOfFirst {
            it.inputTokens + it.cacheReadTokens + it.cacheWriteTokens >= pivot
        }

        // Half the latest input is cheap to recover with a focused compact.
        val savedTokens = latestInput / 2

        return listOf(
            Insight(
                kind = InsightKind.CONTEXT_SATURATION,
                severity = if (latestInput > 160_000) InsightSeverity.CRITICAL else InsightSeverity.WARNING,
                title = "Context near saturation (${latestInput / 1000}k tokens, ${session.turnCount} turns)",
                description = "Long sessions silently lose quality past ~140k tokens. Run `/compact` with " +
                    "focused instructions (\"preserve open files + current TODO\") at a logical breakpoint, " +
                    "or `/clear` when switching tasks.",
                evidence = buildList {
                    add("Latest turn input: ${latestInput / 1000}k tokens")
                    if (pivotIdx >= 0) add("Crossed 70% saturation at turn ${pivotIdx + 1}")
                    add("Total turns: ${session.turnCount}")
                },
                savings = EstimatedSavings(tokens = savedTokens),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }
}
