package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.insights.detectors.BashFloodingDetector
import com.mikepenz.agentbelay.insights.detectors.ClaudeMdBloatDetector
import com.mikepenz.agentbelay.insights.detectors.ColdCacheDetector
import com.mikepenz.agentbelay.insights.detectors.ContextSaturationDetector
import com.mikepenz.agentbelay.insights.detectors.LoopWithoutExitDetector
import com.mikepenz.agentbelay.insights.detectors.McpBloatDetector
import com.mikepenz.agentbelay.insights.detectors.ModelMismatchDetector
import com.mikepenz.agentbelay.insights.detectors.ReasoningOverspendDetector
import com.mikepenz.agentbelay.insights.detectors.RepeatedReadsDetector
import com.mikepenz.agentbelay.insights.detectors.SubagentOverspawnDetector
import com.mikepenz.agentbelay.insights.detectors.UnusedSkillsDetector
import com.mikepenz.agentbelay.insights.detectors.WebFetchRunawayDetector

/**
 * Runs the full detector suite over a [SessionMetrics] and returns the
 * insights, sorted highest-severity-first then highest-savings-first.
 *
 * Insights with no estimated USD savings sort to the end of their severity
 * tier — they're typically qualitative ("trim CLAUDE.md") rather than
 * dollar-quantifiable.
 */
class InsightEngine(
    private val detectors: List<InsightDetector> = defaultDetectors(),
) {
    fun analyze(session: SessionMetrics): List<Insight> {
        val raw = detectors.flatMap { detector ->
            runCatching { detector.detect(session) }.getOrDefault(emptyList())
        }
        return raw.sortedWith(
            compareByDescending<Insight> { it.severity.ordinal }
                .thenByDescending { it.savings.usd ?: -1.0 }
                .thenByDescending { it.savings.tokens ?: -1L }
        )
    }

    companion object {
        fun defaultDetectors(): List<InsightDetector> = listOf(
            ColdCacheDetector(),
            McpBloatDetector(),
            SubagentOverspawnDetector(),
            ReasoningOverspendDetector(),
            ModelMismatchDetector(),
            RepeatedReadsDetector(),
            BashFloodingDetector(),
            ContextSaturationDetector(),
            ClaudeMdBloatDetector(),
            UnusedSkillsDetector(),
            WebFetchRunawayDetector(),
            LoopWithoutExitDetector(),
        )
    }
}
