package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.model.Source

/**
 * Severity tiers for insights — drive icon + color in the UI. Mapped to the
 * existing semantic palette (emerald / warn / danger) by the screen layer.
 */
enum class InsightSeverity {
    /** Soft hint; routine optimization. */
    INFO,
    /** Worth fixing; clear quantitative impact. */
    WARNING,
    /** Major spend driver; act on this first. */
    CRITICAL,
}

/**
 * Stable identifier for each detector — used for analytics, settings
 * (per-detector mute lists in the future), and as the anchor for the
 * `aiEligible` AI-elevation path.
 */
enum class InsightKind {
    COLD_CACHE_THRASH,
    MCP_SCHEMA_BLOAT,
    SUBAGENT_OVERSPAWN,
    REASONING_OVERSPEND,
    OPUS_ON_TRIVIAL_TURNS,
    REPEATED_FILE_READS,
    BASH_OUTPUT_FLOODING,
    CONTEXT_SATURATION,
    CLAUDE_MD_BLOAT,
    UNUSED_SKILLS,
    WEB_FETCH_RUNAWAY,
    LOOP_WITHOUT_EXIT,
}

/**
 * Estimated savings for one insight, both as a token delta (so we can sum
 * across insights) and a USD figure (for the headline). Both are nullable
 * because some insights — like "bloated CLAUDE.md" — only justify a
 * qualitative recommendation; the UI surfaces "—" for those.
 */
data class EstimatedSavings(
    val tokens: Long? = null,
    val usd: Double? = null,
)

/**
 * One actionable proposal surfaced in the Insights tab. Detectors emit zero
 * or more of these per session; the engine sorts them by severity then
 * estimated USD savings.
 *
 * `aiEligible` flags insights whose pre-crafted body benefits from an
 * LLM-personalized rewrite — the UI shows a "Get AI suggestion" button only
 * for those rows (and only when AI insights are enabled in Settings).
 */
data class Insight(
    val kind: InsightKind,
    val severity: InsightSeverity,
    val title: String,
    val description: String,
    val evidence: List<String>,
    val savings: EstimatedSavings,
    val harness: Source,
    val sessionId: String,
    val aiEligible: Boolean = false,
)
