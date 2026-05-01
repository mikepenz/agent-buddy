package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics
import com.mikepenz.agentbelay.model.Source
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * Skills auto-loaded but never invoked. Anthropic's progressive-disclosure
 * model means each loaded skill front-matter contributes to context — easy
 * to recover once you know which never fired.
 *
 * The detector lists skills found under `~/.claude/skills/` (or project
 * `.claude/skills/`) whose name does not appear in any Skill tool call this
 * session. Best-effort filesystem read; injectable for tests.
 */
class UnusedSkillsDetector(
    private val skillsDirResolver: (cwd: String?) -> List<File> = ::resolveOnDisk,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        if (session.harness != Source.CLAUDE_CODE) return emptyList()
        val skillDirs = skillsDirResolver(session.cwd)
        if (skillDirs.isEmpty()) return emptyList()

        val invokedSkills = session.history
            .filter { it.request.hookInput.toolName == "Skill" }
            .mapNotNull { entry ->
                val raw = entry.request.hookInput.toolInput["skill"]
                    ?: entry.request.hookInput.toolInput["name"]
                (raw as? JsonPrimitive)?.contentOrNull
            }
            .toSet()

        val available = skillDirs.flatMap { dir ->
            dir.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        }.toSet()
        val unused = (available - invokedSkills).filter { it.isNotBlank() }
        if (unused.isEmpty()) return emptyList()

        // Anthropic suggests ~15k recoverable per heavyweight skill.
        val savedTokens = (unused.size * 5_000L).coerceAtMost(50_000L)
        return listOf(
            Insight(
                kind = InsightKind.UNUSED_SKILLS,
                severity = InsightSeverity.INFO,
                title = "${unused.size} skills loaded but never invoked",
                description = "Heavyweight skill front-matter loads into every Claude Code session. " +
                    "Move skills you don't use frequently out of the auto-load path, or split them into " +
                    "plugin packs that load only when relevant.",
                evidence = unused.take(10),
                savings = EstimatedSavings(tokens = savedTokens),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }

    companion object {
        fun resolveOnDisk(cwd: String?): List<File> {
            val out = mutableListOf<File>()
            val home = System.getProperty("user.home")
            if (home != null) {
                val global = File(home, ".claude/skills")
                if (global.exists() && global.isDirectory) out.add(global)
            }
            if (cwd != null) {
                val local = File(cwd, ".claude/skills")
                if (local.exists() && local.isDirectory) out.add(local)
            }
            return out
        }
    }
}
