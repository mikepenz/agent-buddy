package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * For Codex / o-series sessions: when reasoning tokens dwarf output tokens
 * by 3:1 over the session, the model is "thinking" much more than it's
 * producing — usually a sign that `reasoning.effort` is set higher than the
 * task warrants. Routine edit/read turns rarely benefit from `high`/`xhigh`.
 *
 * Reasoning tokens are billed at output rates, so a 3:1 ratio means roughly
 * 75% of output spend is reasoning the user didn't see.
 */
class ReasoningOverspendDetector(
    private val ratioThreshold: Double = 3.0,
    private val minReasoningTokens: Long = 5_000L,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        val reasoning = session.totalReasoning
        val output = session.totalOutput.coerceAtLeast(1)
        if (reasoning < minReasoningTokens) return emptyList()
        val ratio = reasoning.toDouble() / output.toDouble()
        if (ratio < ratioThreshold) return emptyList()

        // Treating reasoning as if it were billed at output cost (it is for
        // OpenAI). Cutting effort tier roughly halves reasoning tokens
        // empirically; estimate savings as half of the reasoning fraction
        // of total cost.
        val reasoningShare = reasoning.toDouble() / (reasoning + output).toDouble()
        val savedUsd = session.totalCost * reasoningShare * 0.5

        val pct = ((reasoning.toDouble() / (reasoning + output)) * 100).toInt()
        return listOf(
            Insight(
                kind = InsightKind.REASONING_OVERSPEND,
                severity = if (ratio > 5.0) InsightSeverity.WARNING else InsightSeverity.INFO,
                title = "Reasoning tokens dominate output ($pct% of model output)",
                description = "Reasoning tokens are billed at the output rate but never reach the user. " +
                    "When the ratio sits above 3:1 across a whole session, the effort tier is usually " +
                    "set too aggressively. Lower `model_reasoning_effort` to `medium` (or `minimal` for " +
                    "trivial edits) in `~/.codex/config.toml`. Reserve `high` / `xhigh` for problems where " +
                    "you're explicitly trading time for quality.",
                evidence = listOf(
                    "Reasoning tokens: $reasoning",
                    "Output tokens: $output",
                    "Ratio: ${"%.1f".format(ratio)}:1",
                ),
                savings = EstimatedSavings(
                    tokens = (reasoning * 0.5).toLong(),
                    usd = savedUsd.takeIf { it > 0.01 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }
}
