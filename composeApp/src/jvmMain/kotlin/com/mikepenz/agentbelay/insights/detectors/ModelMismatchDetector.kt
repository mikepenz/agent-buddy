package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Flags Opus turns that are doing work Sonnet would have handled fine —
 * short outputs, read-only tools, no hard reasoning. Opus is roughly 5×
 * Sonnet on input/output; ten such turns is a meaningful chunk of the bill.
 */
class ModelMismatchDetector(
    private val triggerCount: Int = 10,
    private val outputCeiling: Long = 500L,
    private val readOnlyTools: Set<String> = setOf("Read", "Grep", "Glob"),
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        val opusTurns = session.turnsWithModel("claude-opus")
        if (opusTurns.size < triggerCount) return emptyList()

        // Identify "trivial" Opus turns: short output AND only read-only tools
        // were called near that turn (cheap heuristic — we don't have direct
        // tool-call-to-turn correlation, so treat the session as a whole).
        val toolNamesUsed = session.history.map { it.request.hookInput.toolName }.toSet()
        val onlyReadOnly = toolNamesUsed.isNotEmpty() && toolNamesUsed.all { it in readOnlyTools }

        val trivialOpusTurns = opusTurns.count { it.outputTokens < outputCeiling }
        if (trivialOpusTurns < triggerCount) return emptyList()

        // Sonnet runs at ~1/5 of Opus list price. Saved cost ≈ 80% of the
        // trivial Opus turns' aggregate cost.
        val trivialOpusCost = opusTurns
            .filter { it.outputTokens < outputCeiling }
            .sumOf { it.costUsd ?: 0.0 }
        val savedUsd = trivialOpusCost * 0.8

        return listOf(
            Insight(
                kind = InsightKind.OPUS_ON_TRIVIAL_TURNS,
                severity = if (trivialOpusTurns >= 25) InsightSeverity.WARNING else InsightSeverity.INFO,
                title = "Opus is handling $trivialOpusTurns short, read-only turns",
                description = "Opus is ~5× the cost of Sonnet and ~25× of Haiku. Short turns " +
                    "with read-only tools (Read / Grep / Glob) and small outputs almost never benefit. " +
                    "Default to Sonnet (`/model sonnet`) and reach for Opus explicitly for planning, " +
                    "architecture, and gnarly debugging turns where you're trading cost for quality." +
                    if (onlyReadOnly) "" else " (Some non-read-only tools were used too — verify before switching wholesale.)",
                evidence = listOf(
                    "Trivial Opus turns: $trivialOpusTurns of ${opusTurns.size}",
                    "Total Opus turn cost (trivial): \$${"%.2f".format(trivialOpusCost)}",
                ),
                savings = EstimatedSavings(usd = savedUsd.takeIf { it > 0.01 }),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }
}
