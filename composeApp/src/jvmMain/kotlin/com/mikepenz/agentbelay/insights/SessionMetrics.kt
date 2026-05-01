package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord

/**
 * Pre-computed view of a single session that detectors operate on. Built
 * once per session by [com.mikepenz.agentbelay.insights.InsightEngine] from
 * `usage_records` rows and `history` rows so individual detectors don't each
 * re-scan the database.
 *
 * Detectors are pure functions of a [SessionMetrics] — that lets us unit
 * test them with synthetic fixtures without standing up SQLite.
 */
data class SessionMetrics(
    val harness: Source,
    val sessionId: String,
    val cwd: String?,
    /** Dominant model across the session. May not equal [turns.first().model]. */
    val model: String?,
    val turns: List<UsageRecord>,
    /**
     * Tool calls, in chronological order, the user (or risk-analyzer) took
     * decisions on. Joined by `session_id`; not strictly 1:1 with [turns].
     */
    val history: List<ApprovalResult>,
) {
    val turnCount: Int get() = turns.size

    val totalInput: Long get() = turns.sumOf { it.inputTokens }
    val totalOutput: Long get() = turns.sumOf { it.outputTokens }
    val totalCacheRead: Long get() = turns.sumOf { it.cacheReadTokens }
    val totalCacheWrite: Long get() = turns.sumOf { it.cacheWriteTokens }
    val totalReasoning: Long get() = turns.sumOf { it.reasoningTokens }
    val totalCost: Double get() = turns.sumOf { it.costUsd ?: 0.0 }

    /**
     * Cache-read share over all input-side tokens. Anthropic's recommended
     * healthy floor is ~70%; below 30% indicates the prompt prefix is
     * invalidating turn-to-turn.
     */
    val cacheReadRatio: Double
        get() {
            val denom = totalInput + totalCacheRead + totalCacheWrite
            if (denom <= 0) return 0.0
            return totalCacheRead.toDouble() / denom.toDouble()
        }

    /** Turns whose model name starts with the given prefix (case-insensitive). */
    fun turnsWithModel(prefix: String): List<UsageRecord> =
        turns.filter { it.model?.lowercase()?.startsWith(prefix.lowercase()) == true }

    /** Approval rows whose tool name matches [toolName] exactly. */
    fun calls(toolName: String): List<ApprovalResult> =
        history.filter { it.request.hookInput.toolName == toolName }
}
