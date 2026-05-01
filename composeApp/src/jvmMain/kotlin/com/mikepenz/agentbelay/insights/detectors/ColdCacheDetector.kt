package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Detects sessions whose prompt cache isn't sticking — typically because
 * something near the top of the system prompt (date stamp, git status, "now"
 * timestamp) is invalidating between turns. A healthy Claude Code session
 * sits at 70–90% cache-read ratio; below 30% with enough turns to ride the
 * cache curve is a clear smell.
 *
 * Savings model: re-pricing what's currently billed as fresh input at the
 * cache-read rate (10% of input list price for Anthropic models). That's a
 * conservative "if the cache had hit, this is what you'd have paid" delta.
 */
class ColdCacheDetector(
    private val minTurns: Int = 20,
    private val ratioThreshold: Double = 0.30,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        if (session.turnCount < minTurns) return emptyList()
        val ratio = session.cacheReadRatio
        if (ratio >= ratioThreshold) return emptyList()

        val savedTokens = session.totalInput - (session.totalCacheRead.coerceAtLeast(0))
        // Conservative: 90% of input cost is recoverable if the prefix had
        // cached. Per-turn cost USD already factors in the actual mix, so
        // scaling it by (1 - ratio) overstates only when output dominates;
        // clamp at 50% of total cost.
        val savedUsd = (session.totalCost * (1 - ratio).coerceIn(0.0, 1.0))
            .coerceAtMost(session.totalCost * 0.5)

        val pct = (ratio * 100).toInt()
        return listOf(
            Insight(
                kind = InsightKind.COLD_CACHE_THRASH,
                severity = if (ratio < 0.10) InsightSeverity.CRITICAL else InsightSeverity.WARNING,
                title = "Prompt cache is missing — only $pct% of inputs hit cache",
                description = "Healthy Claude Code sessions cache 70–90% of inputs. Yours is at $pct%, which usually means something near the top of the system prompt is changing between turns: a timestamp, git status, or a hook that mutates context.",
                evidence = listOf(
                    "Cache-read tokens: ${session.totalCacheRead}",
                    "Uncached input tokens: ${session.totalInput}",
                    "Across ${session.turnCount} turns",
                ),
                savings = EstimatedSavings(
                    tokens = savedTokens.coerceAtLeast(0),
                    usd = savedUsd.takeIf { it > 0 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = true,
            )
        )
    }
}
