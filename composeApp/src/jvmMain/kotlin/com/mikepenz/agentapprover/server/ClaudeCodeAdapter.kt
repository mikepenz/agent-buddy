package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ClaudeCodeAdapter {

    private val logger = Logger.withTag("ClaudeCodeAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val sessionId = root["session_id"]?.jsonPrimitive?.content ?: return null.also {
                logger.w { "Missing session_id" }
            }
            val cwd = root["cwd"]?.jsonPrimitive?.content ?: return null.also {
                logger.w { "Missing cwd" }
            }
            val toolName = root["tool_name"]?.jsonPrimitive?.content ?: return null.also {
                logger.w { "Missing tool_name" }
            }
            val toolInput = root["tool_input"]?.jsonObject ?: return null.also {
                logger.w { "Missing tool_input" }
            }

            val toolType = when (toolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan", "ExitPlanMode" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.CLAUDE_CODE,
                toolName = toolName,
                toolType = toolType,
                toolInput = toolInput,
                sessionId = sessionId,
                cwd = cwd,
                timestamp = Clock.System.now(),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse hook JSON" }
            null
        }
    }
}
