package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.capability.AgentTarget
import com.mikepenz.agentapprover.capability.CapabilityEngine
import com.mikepenz.agentapprover.capability.HookEvent
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val logger = Logger.withTag("CapabilityRoute")

/**
 * Receives Claude Code `UserPromptSubmit` hook events and returns
 * `additionalContext` computed from enabled [CapabilityEngine] modules.
 *
 * The handler never parses the prompt body — it only needs to know the
 * event name to pick the right injection text. The response shape follows
 * Claude Code's documented hook schema:
 *
 *   {
 *     "hookSpecificOutput": {
 *       "hookEventName": "UserPromptSubmit",
 *       "additionalContext": "..."
 *     }
 *   }
 *
 * If no capability contributes context, an empty object is returned so
 * Claude Code treats the call as a no-op.
 */
fun Route.capabilityRoute(engine: CapabilityEngine) {
    post("/capability/inject") {
        // Drain the body so Netty can recycle the connection, but we don't
        // parse it — the injection is independent of the prompt content.
        runCatching { call.receiveText() }

        val text = engine.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.CLAUDE_CODE)
        if (text.isBlank()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }
        val body = buildJsonObject {
            put("hookSpecificOutput", buildJsonObject {
                put("hookEventName", "UserPromptSubmit")
                put("additionalContext", text)
            })
        }.toString()
        call.respondText(body, contentType = ContentType.Application.Json)
    }

    post("/capability/inject-copilot") {
        runCatching { call.receiveText() }

        val text = engine.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.COPILOT_CLI)
        if (text.isBlank()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }
        // Copilot CLI's userPromptSubmitted hook reads stdout as injected
        // context. The bridge script pipes this JSON back, and Copilot CLI
        // honors `hookSpecificOutput.additionalContext` the same way Claude
        // Code does for its PascalCase equivalent.
        val body = buildJsonObject {
            put("hookSpecificOutput", buildJsonObject {
                put("hookEventName", "userPromptSubmitted")
                put("additionalContext", text)
            })
        }.toString()
        call.respondText(body, contentType = ContentType.Application.Json)
        logger.v { "Served capability injection (${text.length} chars) to Copilot CLI" }
    }
}
