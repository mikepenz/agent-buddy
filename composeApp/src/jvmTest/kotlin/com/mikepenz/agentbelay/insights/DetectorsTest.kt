package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.insights.InsightFixtures.bash
import com.mikepenz.agentbelay.insights.InsightFixtures.edit
import com.mikepenz.agentbelay.insights.InsightFixtures.read
import com.mikepenz.agentbelay.insights.InsightFixtures.session
import com.mikepenz.agentbelay.insights.InsightFixtures.task
import com.mikepenz.agentbelay.insights.InsightFixtures.turn
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
import com.mikepenz.agentbelay.model.Source
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectorsTest {

    // ── ColdCacheDetector ─────────────────────────────────────────────────

    @Test
    fun coldCache_triggers_when_cache_ratio_low() {
        val turns = (0 until 25).map {
            turn(input = 5_000, output = 100, cacheRead = 500, cost = 0.05)
        }
        val insights = ColdCacheDetector().detect(session(turns))
        assertEquals(1, insights.size)
        assertEquals(InsightKind.COLD_CACHE_THRASH, insights.first().kind)
        assertTrue((insights.first().savings.usd ?: 0.0) > 0.0)
    }

    @Test
    fun coldCache_silent_when_cache_ratio_healthy() {
        val turns = (0 until 25).map {
            turn(input = 1_000, output = 100, cacheRead = 9_000, cost = 0.05)
        }
        assertEquals(emptyList(), ColdCacheDetector().detect(session(turns)))
    }

    @Test
    fun coldCache_silent_below_minTurns() {
        val turns = (0 until 5).map {
            turn(input = 5_000, output = 100, cacheRead = 100, cost = 0.05)
        }
        assertEquals(emptyList(), ColdCacheDetector().detect(session(turns)))
    }

    // ── McpBloatDetector ──────────────────────────────────────────────────

    @Test
    fun mcpBloat_triggers_on_huge_first_turn_with_no_mcp_calls() {
        val turns = listOf(
            turn(input = 30_000, output = 200, cost = 0.20),
            turn(input = 1_000, output = 100, cacheRead = 28_000, cost = 0.10),
        )
        val out = McpBloatDetector().detect(session(turns, history = listOf(bash("ls"))))
        assertEquals(1, out.size)
        assertEquals(InsightKind.MCP_SCHEMA_BLOAT, out.first().kind)
    }

    @Test
    fun mcpBloat_silent_when_first_turn_small() {
        val turns = listOf(turn(input = 6_000, output = 100, cost = 0.05))
        assertEquals(emptyList(), McpBloatDetector().detect(session(turns)))
    }

    @Test
    fun mcpBloat_silent_when_many_mcp_tools_actually_used() {
        val turns = listOf(turn(input = 30_000, output = 200, cost = 0.20))
        val history = listOf(
            InsightFixtures.approval("mcp__github__create_issue"),
            InsightFixtures.approval("mcp__linear__create_ticket"),
            InsightFixtures.approval("mcp__notion__write_page"),
        )
        assertEquals(emptyList(), McpBloatDetector().detect(session(turns, history)))
    }

    // ── SubagentOverspawnDetector ─────────────────────────────────────────

    @Test
    fun subagentOverspawn_triggers_at_threshold() {
        val turns = listOf(turn(input = 5_000, output = 100, cost = 0.05))
        val history = (0 until 4).map { task(offsetMillis = it * 1_000L) }
        val out = SubagentOverspawnDetector().detect(session(turns, history))
        assertEquals(1, out.size)
        assertTrue((out.first().savings.tokens ?: 0L) >= 60_000L)
    }

    @Test
    fun subagentOverspawn_silent_below_threshold() {
        val turns = listOf(turn(input = 5_000, output = 100, cost = 0.05))
        val history = listOf(task(), task())
        assertEquals(emptyList(), SubagentOverspawnDetector().detect(session(turns, history)))
    }

    // ── ReasoningOverspendDetector ────────────────────────────────────────

    @Test
    fun reasoningOverspend_triggers_when_reasoning_dominates() {
        val turns = (0 until 10).map {
            turn(input = 1_000, output = 200, reasoning = 800, cost = 0.05, model = "gpt-5-codex")
        }
        val out = ReasoningOverspendDetector().detect(session(turns))
        assertEquals(1, out.size)
    }

    @Test
    fun reasoningOverspend_silent_when_output_dominates() {
        val turns = (0 until 10).map {
            turn(input = 1_000, output = 800, reasoning = 200, cost = 0.05, model = "gpt-5-codex")
        }
        assertEquals(emptyList(), ReasoningOverspendDetector().detect(session(turns)))
    }

    // ── ModelMismatchDetector ─────────────────────────────────────────────

    @Test
    fun modelMismatch_triggers_when_opus_handles_trivial_turns() {
        val turns = (0 until 12).map {
            turn(input = 1_000, output = 200, cost = 0.10, model = "claude-opus-4-7")
        }
        val history = (0 until 12).map { read("/tmp/a.txt", offsetMillis = it * 1_000L) }
        val out = ModelMismatchDetector().detect(session(turns, history, model = "claude-opus-4-7"))
        assertEquals(1, out.size)
        assertEquals(InsightKind.OPUS_ON_TRIVIAL_TURNS, out.first().kind)
    }

    @Test
    fun modelMismatch_silent_when_outputs_are_substantial() {
        val turns = (0 until 12).map {
            turn(input = 1_000, output = 1_500, cost = 0.10, model = "claude-opus-4-7")
        }
        assertEquals(emptyList(), ModelMismatchDetector().detect(session(turns)))
    }

    // ── RepeatedReadsDetector ─────────────────────────────────────────────

    @Test
    fun repeatedReads_flags_thrice_read_path() {
        val turns = listOf(turn(input = 1_000, output = 100, cost = 0.01))
        val history = listOf(
            read("/x.kt", offsetMillis = 1_000),
            read("/x.kt", offsetMillis = 2_000),
            read("/x.kt", offsetMillis = 3_000),
        )
        val out = RepeatedReadsDetector().detect(session(turns, history))
        assertEquals(1, out.size)
        assertTrue(out.first().evidence.first().contains("/x.kt"))
    }

    @Test
    fun repeatedReads_silent_when_intervening_edit() {
        val turns = listOf(turn(input = 1_000, output = 100, cost = 0.01))
        val history = listOf(
            read("/x.kt", offsetMillis = 1_000),
            read("/x.kt", offsetMillis = 2_000),
            edit("/x.kt", offsetMillis = 2_500),
            read("/x.kt", offsetMillis = 3_000),
        )
        assertEquals(emptyList(), RepeatedReadsDetector().detect(session(turns, history)))
    }

    // ── BashFloodingDetector ──────────────────────────────────────────────

    @Test
    fun bashFlooding_triggers_on_repeated_npm_install() {
        val turns = listOf(turn(input = 2_000, output = 100, cost = 0.02))
        val history = listOf(
            bash("npm install", offsetMillis = 1_000),
            bash("cargo build --release", offsetMillis = 2_000),
            bash("find . -name '*.kt'", offsetMillis = 3_000),
        )
        val out = BashFloodingDetector().detect(session(turns, history))
        assertEquals(1, out.size)
    }

    @Test
    fun bashFlooding_silent_when_few_matches() {
        val turns = listOf(turn(input = 2_000, output = 100, cost = 0.02))
        val history = listOf(bash("ls"), bash("pwd"))
        assertEquals(emptyList(), BashFloodingDetector().detect(session(turns, history)))
    }

    // ── ContextSaturationDetector ─────────────────────────────────────────

    @Test
    fun contextSaturation_triggers_when_saturated_and_long() {
        val turns = (0 until 90).map { i ->
            val input = if (i < 89) 5_000L else 145_000L
            turn(input = input, output = 200, cost = 0.10, offsetMillis = i * 1_000L)
        }
        val out = ContextSaturationDetector().detect(session(turns))
        assertEquals(1, out.size)
    }

    @Test
    fun contextSaturation_silent_short_session() {
        val turns = listOf(turn(input = 145_000, output = 200, cost = 0.20))
        assertEquals(emptyList(), ContextSaturationDetector().detect(session(turns)))
    }

    // ── ClaudeMdBloatDetector ─────────────────────────────────────────────

    @Test
    fun claudeMdBloat_triggers_when_excess_input_and_big_md() {
        val turns = listOf(
            turn(input = 16_000, output = 200, cost = 0.10),
            turn(input = 1_000, output = 100, cacheRead = 14_000, cost = 0.05),
        )
        val fakeFile = File.createTempFile("CLAUDE", ".md").apply {
            writeText((1..300).joinToString("\n") { "line $it" })
            deleteOnExit()
        }
        val detector = ClaudeMdBloatDetector(cwdFileResolver = { _, _ -> fakeFile })
        val out = detector.detect(session(turns))
        assertEquals(1, out.size)
        assertEquals(InsightKind.CLAUDE_MD_BLOAT, out.first().kind)
    }

    @Test
    fun claudeMdBloat_silent_when_md_small() {
        val turns = listOf(turn(input = 16_000, output = 200, cost = 0.10))
        val fakeFile = File.createTempFile("small", ".md").apply {
            writeText("just a few lines")
            deleteOnExit()
        }
        val detector = ClaudeMdBloatDetector(cwdFileResolver = { _, _ -> fakeFile })
        assertEquals(emptyList(), detector.detect(session(turns)))
    }

    // ── UnusedSkillsDetector ──────────────────────────────────────────────

    @Test
    fun unusedSkills_lists_skills_never_invoked() {
        val tempDir = File.createTempFile("skills", "").apply {
            delete(); mkdirs(); deleteOnExit()
        }
        File(tempDir, "skill-a").mkdirs()
        File(tempDir, "skill-b").mkdirs()
        val detector = UnusedSkillsDetector(skillsDirResolver = { listOf(tempDir) })
        val out = detector.detect(session(listOf(turn(input = 100, output = 10))))
        assertEquals(1, out.size)
        assertEquals(setOf("skill-a", "skill-b"), out.first().evidence.toSet())
    }

    @Test
    fun unusedSkills_silent_for_non_claude_harness() {
        val detector = UnusedSkillsDetector(skillsDirResolver = { error("should not be called") })
        val out = detector.detect(session(listOf(turn(input = 100, output = 10)), harness = Source.CODEX))
        assertEquals(emptyList(), out)
    }

    // ── WebFetchRunawayDetector ───────────────────────────────────────────

    @Test
    fun webFetchRunaway_triggers_on_long_chain() {
        val turns = listOf(turn(input = 5_000, output = 200, cost = 0.05))
        val history = (0 until 6).map {
            InsightFixtures.approval("WebFetch", offsetMillis = it * 1_000L)
        }
        val out = WebFetchRunawayDetector().detect(session(turns, history))
        assertEquals(1, out.size)
    }

    @Test
    fun webFetchRunaway_silent_when_chain_broken_by_edits() {
        val turns = listOf(turn(input = 5_000, output = 200, cost = 0.05))
        val history = listOf(
            InsightFixtures.approval("WebFetch", offsetMillis = 1_000),
            InsightFixtures.approval("WebFetch", offsetMillis = 2_000),
            edit("/x.kt", offsetMillis = 2_500),
            InsightFixtures.approval("WebFetch", offsetMillis = 3_000),
        )
        assertEquals(emptyList(), WebFetchRunawayDetector().detect(session(turns, history)))
    }

    // ── LoopWithoutExitDetector ───────────────────────────────────────────

    @Test
    fun loopWithoutExit_triggers_on_falling_cache_ratio() {
        // First half: healthy 80% cache ratio. Last quartile: 10%.
        val turns = buildList {
            repeat(60) { add(turn(input = 1_000, cacheRead = 4_000, output = 100, cost = 0.05)) }
            repeat(20) { add(turn(input = 5_000, cacheRead = 500, output = 100, cost = 0.10)) }
        }
        val out = LoopWithoutExitDetector().detect(session(turns))
        assertEquals(1, out.size)
    }

    @Test
    fun loopWithoutExit_silent_when_cache_ratio_stable() {
        val turns = (0 until 60).map { turn(input = 1_000, cacheRead = 4_000, output = 100, cost = 0.05) }
        assertEquals(emptyList(), LoopWithoutExitDetector().detect(session(turns)))
    }

    // ── Engine integration ────────────────────────────────────────────────

    @Test
    fun engine_sorts_critical_first_then_by_savings() {
        val turns = (0 until 25).map { turn(input = 5_000, output = 100, cacheRead = 200, cost = 0.05) }
        val history = (0 until 5).map { task(offsetMillis = it * 1_000L) }
        val results = InsightEngine().analyze(session(turns, history))
        // ColdCacheDetector triggers (severity depends on ratio); subagent triggers warning/info.
        // Just sanity-check ordering: highest severity should be first.
        assertTrue(results.isNotEmpty())
        for (i in 0 until results.size - 1) {
            assertTrue(
                results[i].severity.ordinal >= results[i + 1].severity.ordinal,
                "results not sorted by severity at $i",
            )
        }
    }
}
