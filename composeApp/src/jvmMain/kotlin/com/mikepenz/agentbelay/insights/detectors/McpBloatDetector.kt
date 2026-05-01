package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Heuristic for "lots of MCP servers loaded but not really used".
 *
 * Each MCP server stuffs its tool schemas into the system prompt on session
 * start. Claude Code's bare-bones first-turn input runs ~4–6k tokens; once
 * we cross 25k it's almost always tool-schema bloat. If the user then never
 * fires more than one or two MCP tools across the whole session, those
 * schemas are pure waste.
 *
 * MCP tool calls are recognized by Claude Code's `mcp__server__tool` naming
 * convention, plus a fallback heuristic for tools containing `__`.
 */
class McpBloatDetector(
    private val firstTurnInputThreshold: Long = 25_000,
    private val maxMcpToolsForFlag: Int = 2,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        val firstTurn = session.turns.firstOrNull() ?: return emptyList()
        val firstInput = firstTurn.inputTokens + firstTurn.cacheReadTokens + firstTurn.cacheWriteTokens
        if (firstInput < firstTurnInputThreshold) return emptyList()

        val mcpTools = session.history
            .map { it.request.hookInput.toolName }
            .filter { it.startsWith("mcp__") || it.contains("__") }
            .toSet()
        if (mcpTools.size > maxMcpToolsForFlag) return emptyList()

        val excess = (firstInput - 5_000L).coerceAtLeast(0)
        // If we'd cut the first-turn baseline, every subsequent turn that
        // currently rides the cache would re-cache cheaper. Apply the same
        // saved tokens to each turn the prefix is reused — bounded.
        val savedPerTurn = excess
        val savedTokens = savedPerTurn * session.turnCount.coerceAtMost(50)
        val savedUsdGuess = (session.totalCost * 0.25).coerceAtMost(session.totalCost)

        return listOf(
            Insight(
                kind = InsightKind.MCP_SCHEMA_BLOAT,
                severity = if (firstInput > 60_000) InsightSeverity.CRITICAL else InsightSeverity.WARNING,
                title = "First-turn prompt is heavy (~${firstInput / 1000}k tokens) with little MCP use",
                description = "Loaded MCP servers inject their full tool schemas into every session's system prompt. " +
                    "The first turn used ${firstInput} tokens but only ${mcpTools.size} MCP tool(s) actually fired — " +
                    "most of that schema is dead weight. Disable unused servers in `.mcp.json` / `~/.claude.json`, " +
                    "or scope them per-project, or enable Anthropic's Tool Search Tool.",
                evidence = buildList {
                    add("First-turn input: $firstInput tokens (Claude Code baseline ~4–6k)")
                    add("MCP tools invoked: ${if (mcpTools.isEmpty()) "none" else mcpTools.joinToString(", ")}")
                    add("Across ${session.turnCount} turns")
                },
                savings = EstimatedSavings(
                    tokens = savedTokens,
                    usd = savedUsdGuess.takeIf { it > 0 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }
}
