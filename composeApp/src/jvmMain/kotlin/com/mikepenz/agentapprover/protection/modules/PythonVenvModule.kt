package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule
import com.mikepenz.agentapprover.protection.parser.ParsedCommand
import com.mikepenz.agentapprover.protection.parser.SimpleCommand
import com.mikepenz.agentapprover.protection.parser.allSimpleCommands

object PythonVenvModule : ProtectionModule {
    override val id = "python_venv"
    override val name = "Python Virtual Environment"
    override val description = "Enforces use of virtual environments and uv for Python commands."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        BarePython,
        BarePip,
        PythonMVenv,
    )

    private val pythonNames = setOf("python", "python2", "python3")

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    /**
     * Returns true if a `source <...>/bin/activate` (or `. <...>/bin/activate`) simple command
     * appears in the chain strictly before [target] (compared by reference).
     */
    private fun venvActivatedBefore(parsed: ParsedCommand, target: SimpleCommand): Boolean {
        // Walk pipelines in order; as soon as we encounter [target] by reference, stop.
        for (entry in parsed.pipelines) {
            for (cmd in entry.pipeline.commands) {
                if (cmd === target) return false
                if (isActivateCommand(cmd)) return true
            }
        }
        return false
    }

    private fun isActivateCommand(sc: SimpleCommand): Boolean {
        val name = sc.commandName
        if (name != "source" && name != ".") return false
        return sc.args.any { a ->
            val lit = a.literal ?: return@any false
            lit.endsWith("/bin/activate") &&
                (lit.contains(".venv") || lit.contains("venv") || lit.contains("env"))
        }
    }

    /**
     * True iff the python command is a "known safe" variant:
     *  - `.venv/bin/python ...` (by raw name, not basename)
     *  - `uv run python ...` (uv is the outer command; not matched here — skipped earlier)
     *  - `python --version`
     */
    private fun isSafePython(sc: SimpleCommand): Boolean {
        // Raw (full) path check: name.literal containing `.venv/bin/python`.
        val rawName = sc.rawName ?: return false
        if (rawName.contains(".venv/bin/python")) return true
        if (sc.hasLongFlag("--version") || sc.hasLiteralArg("-V")) return true
        return false
    }

    private object BarePython : ProtectionRule {
        override val id = "bare_python"
        override val name = "Bare python command"
        override val description = "Detects bare python command without virtual environment."
        override val correctiveHint = "Use .venv/bin/python or uv run python instead. Create a venv with: uv venv"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val parsed = CommandParser.parsedBash(hookInput) ?: return null
            // Note: `uv run python ...` never surfaces a python simple command (python is just
            // an arg of `uv`), so we don't need an explicit uv allow-check here. Any python-named
            // simple command is by definition a bare invocation of python.
            val offending = parsed.allSimpleCommands().firstOrNull { sc ->
                if (sc.commandName !in pythonNames) return@firstOrNull false
                if (isSafePython(sc)) return@firstOrNull false
                if (venvActivatedBefore(parsed, sc)) return@firstOrNull false
                true
            } ?: return null
            val raw = offending.rawName ?: offending.commandName ?: "python"
            return hit(id, "Use .venv/bin/python or uv run python instead. Create a venv with: uv venv (at: $raw)")
        }
    }

    private object BarePip : ProtectionRule {
        override val id = "bare_pip"
        override val name = "Bare pip install"
        override val description = "Detects pip install without virtual environment."
        override val correctiveHint =
            "Use uv pip install or activate a virtual environment first. Create one with: uv venv && source .venv/bin/activate"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val parsed = CommandParser.parsedBash(hookInput) ?: return null
            // `uv pip install` surfaces as a `uv` simple command with pip/install as args — it has
            // no standalone pip simple command, so the filter below naturally excludes it.
            val offending = parsed.allSimpleCommands().firstOrNull { sc ->
                val name = sc.commandName ?: return@firstOrNull false
                val isBarePip = (name == "pip" || name == "pip3") && sc.hasLiteralArg("install")
                val isPythonMPipInstall = name in pythonNames && sc.hasLiteralArg("-m") &&
                    sc.hasLiteralArg("pip") && sc.hasLiteralArg("install")
                if (!isBarePip && !isPythonMPipInstall) return@firstOrNull false
                if (venvActivatedBefore(parsed, sc)) return@firstOrNull false
                true
            } ?: return null
            val raw = offending.rawName ?: "pip"
            return hit(id, "Use uv pip install or activate a venv first (at: $raw)")
        }
    }

    private object PythonMVenv : ProtectionRule {
        override val id = "python_m_venv"
        override val name = "Python -m venv"
        override val description = "Detects python -m venv and suggests uv venv instead."
        override val correctiveHint = "Use uv venv instead of python -m venv. uv is faster and more reliable."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val parsed = CommandParser.parsedBash(hookInput) ?: return null
            val match = parsed.allSimpleCommands().any { sc ->
                sc.commandName in pythonNames && sc.hasLiteralArg("-m") && sc.hasLiteralArg("venv")
            }
            return if (match) hit(id, "Use uv venv instead. uv is faster and more reliable.") else null
        }
    }

}
