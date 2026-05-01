package com.mikepenz.agentbelay.storage

import com.mikepenz.agentbelay.model.Source

/**
 * Per-harness rollup for the Usage tab. Token totals are pre-summed across all
 * records within the queried window; cost is summed at row-write time so this
 * stays cheap even at high cardinality.
 */
data class UsageHarnessTotals(
    val harness: Source,
    val requests: Int,
    val sessions: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val reasoningTokens: Long,
    val costUsd: Double,
)

/**
 * One bar of the Activity sparkline. [epochDay] is `ts / 86_400_000` so two
 * records on the same UTC day collapse — the UI treats it as an opaque ordinal.
 */
data class UsageDailyCount(
    val epochDay: Long,
    val requests: Int,
)

/**
 * Lightweight roll-up of one session, used by the Insights tab as the
 * left-rail entry. The dominant model wins ties (see
 * `DatabaseStorage.listRecentSessions`).
 */
data class SessionSummary(
    val harness: Source,
    val sessionId: String,
    val model: String?,
    val firstTsMillis: Long,
    val lastTsMillis: Long,
    val turnCount: Int,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalCacheReadTokens: Long,
    val totalCostUsd: Double,
)
