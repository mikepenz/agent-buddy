package com.mikepenz.agentapprover.model

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun approvalRequestRoundTrip() {
        val request = ApprovalRequest(
            id = "test-id",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = "session-1",
                toolName = "Bash",
                toolInput = mapOf("command" to JsonPrimitive("npm test")),
                cwd = "/tmp",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val encoded = json.encodeToString(ApprovalRequest.serializer(), request)
        val decoded = json.decodeFromString(ApprovalRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun approvalResultRoundTrip() {
        val request = ApprovalRequest(
            id = "test-id",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = "session-1",
                toolName = "Bash",
                toolInput = mapOf("command" to JsonPrimitive("npm test")),
                cwd = "/tmp",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val result = ApprovalResult(
            request = request,
            decision = Decision.APPROVED,
            feedback = "Looks good",
            riskAnalysis = RiskAnalysis(risk = 3, label = "Moderate", message = "Medium risk"),
            rawResponseJson = """{"approved":true}""",
            decidedAt = Clock.System.now(),
        )
        val encoded = json.encodeToString(ApprovalResult.serializer(), result)
        val decoded = json.decodeFromString(ApprovalResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun approvalResultWithNullFields() {
        val request = ApprovalRequest(
            id = "test-id-2",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.ASK_USER_QUESTION,
            hookInput = HookInput(
                sessionId = "session-2",
                toolName = "Ask",
                cwd = "/home",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val result = ApprovalResult(
            request = request,
            decision = Decision.CANCELLED_BY_CLIENT,
            feedback = null,
            riskAnalysis = null,
            rawResponseJson = null,
            decidedAt = Clock.System.now(),
        )
        val encoded = json.encodeToString(ApprovalResult.serializer(), result)
        val decoded = json.decodeFromString(ApprovalResult.serializer(), encoded)
        assertEquals(result, decoded)
        assertNull(decoded.feedback)
        assertNull(decoded.riskAnalysis)
        assertNull(decoded.rawResponseJson)
    }

    @Test
    fun hookInputWithPermissionSuggestions() {
        val input = HookInput(
            sessionId = "session-1",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("ls -la")),
            permissionSuggestions = listOf(
                PermissionSuggestion(
                    type = "addRules",
                    rules = listOf(RuleEntry(toolName = "Bash", ruleContent = "ls *")),
                    behavior = "allow",
                    destination = "projectSettings",
                ),
            ),
            cwd = "/tmp",
        )
        val encoded = json.encodeToString(HookInput.serializer(), input)
        val decoded = json.decodeFromString(HookInput.serializer(), encoded)
        assertEquals(input, decoded)
        assertEquals(1, decoded.permissionSuggestions.size)
        assertEquals("Bash", decoded.permissionSuggestions[0].rules?.first()?.toolName)
        assertEquals("ls *", decoded.permissionSuggestions[0].rules?.first()?.ruleContent)
    }

    @Test
    fun appSettingsRoundTrip() {
        val settings = AppSettings()
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings, decoded)
        assertEquals(19532, decoded.serverPort)
        assertEquals(true, decoded.alwaysOnTop)
        assertEquals(240, decoded.defaultTimeoutSeconds)
    }
}
