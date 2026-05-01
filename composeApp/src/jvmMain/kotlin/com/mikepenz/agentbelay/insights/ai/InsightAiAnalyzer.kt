package com.mikepenz.agentbelay.insights.ai

import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.SessionMetrics
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.RiskAnalyzer

/**
 * Adapter that elevates a heuristic [Insight] into a personalized
 * [AiSuggestion] using whichever [RiskAnalyzer] backend the user already
 * configured (Claude / Copilot / Ollama / OpenAI-compat).
 *
 * Reuses the existing risk-analyzer plumbing rather than spinning up a
 * second LLM client — see `risk/RiskAnalyzer.analyzeText` for the per-
 * backend prompt path. The user message we send is deliberately tiny
 * (the single insight + minimal session context) to keep the call cheap.
 *
 * The [analyzerHolder] dependency is provided so the active analyzer
 * resolves at call time, not construction time — a Settings-side switch
 * to a different backend takes effect on the next click.
 */
class InsightAiAnalyzer(
    private val analyzerHolder: ActiveRiskAnalyzerHolder,
    private val systemPrompt: String = SystemPrompts.DEFAULT,
) {
    /**
     * Sends the pre-detected [insight] (with a small session summary) to the
     * active analyzer and parses the response into [AiSuggestion].
     *
     * Returns [Result.failure] when:
     *  - No analyzer is currently configured.
     *  - The active analyzer doesn't implement `analyzeText` (older backend).
     *  - The backend call itself failed (network, timeout, etc.).
     */
    suspend fun elevate(insight: Insight, session: SessionMetrics): Result<AiSuggestion> {
        val analyzer = analyzerHolder.analyzer.value
            ?: return Result.failure(IllegalStateException("No risk analyzer configured"))
        val prompt = buildPrompt(insight, session)
        val raw = analyzer.analyzeText(systemPrompt = systemPrompt, userPrompt = prompt)
            .getOrElse { return Result.failure(it) }
        return Result.success(parse(raw))
    }

    /**
     * Builds the user-facing prompt: enough context for the model to give
     * specific advice, but bounded so we stay under ~1k input tokens. We
     * pass the insight's title, evidence, harness, and dominant model —
     * never raw tool inputs (would leak file contents) or prompts.
     */
    internal fun buildPrompt(insight: Insight, session: SessionMetrics): String = buildString {
        appendLine("INSIGHT: ${insight.title}")
        appendLine("KIND: ${insight.kind}")
        appendLine("SEVERITY: ${insight.severity}")
        appendLine()
        appendLine("HARNESS: ${session.harness} · model=${session.model ?: "(mixed)"} · turns=${session.turnCount}")
        appendLine("TOTALS: input=${session.totalInput} output=${session.totalOutput} cacheRead=${session.totalCacheRead} cost=\$${"%.2f".format(session.totalCost)}")
        appendLine()
        appendLine("PRE-CRAFTED ADVICE:")
        appendLine(insight.description)
        if (insight.evidence.isNotEmpty()) {
            appendLine()
            appendLine("EVIDENCE:")
            insight.evidence.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("Respond using the TITLE / BODY / ACTION format.")
    }

    /**
     * Tolerant parser: accepts the strict `TITLE:`/`BODY:`/`ACTION:` format
     * but degrades gracefully when the model goes off-script. The raw text
     * is always preserved on the result for debugging.
     */
    internal fun parse(raw: String): AiSuggestion {
        var title: String? = null
        var body: String? = null
        var action: String? = null
        var current: String? = null
        val builder = StringBuilder()

        fun flush() {
            val text = builder.toString().trim()
            when (current) {
                "TITLE" -> title = text
                "BODY" -> body = text
                "ACTION" -> action = text.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
            }
            builder.clear()
        }

        raw.lineSequence().forEach { line ->
            val labelMatch = Regex("^(TITLE|BODY|ACTION):\\s*(.*)$").matchEntire(line.trimStart())
            if (labelMatch != null) {
                flush()
                current = labelMatch.groupValues[1]
                builder.append(labelMatch.groupValues[2])
            } else if (current != null) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(line)
            }
        }
        flush()

        return AiSuggestion(
            title = title?.takeIf { it.isNotBlank() } ?: "AI suggestion",
            body = body?.takeIf { it.isNotBlank() } ?: raw.trim(),
            action = action,
            rawText = raw,
        )
    }
}
