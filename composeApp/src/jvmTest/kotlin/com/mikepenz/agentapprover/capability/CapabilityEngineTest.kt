package com.mikepenz.agentapprover.capability

import com.mikepenz.agentapprover.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentapprover.model.CapabilityModuleSettings
import com.mikepenz.agentapprover.model.CapabilitySettings
import com.mikepenz.agentapprover.model.CompressionIntensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityEngineTest {

    private fun engine(settings: CapabilitySettings) = CapabilityEngine(
        modules = listOf(ResponseCompressionCapability),
        settingsProvider = { settings },
    )

    @Test
    fun `disabled modules contribute nothing`() {
        val e = engine(CapabilitySettings())
        assertTrue(e.enabledModules().isEmpty())
        assertTrue(e.requiredHookEvents().isEmpty())
        assertEquals("", e.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.CLAUDE_CODE))
    }

    @Test
    fun `enabled module declares its required hook events`() {
        val e = engine(
            CapabilitySettings(
                modules = mapOf(
                    ResponseCompressionCapability.id to CapabilityModuleSettings(
                        enabled = true,
                        intensity = CompressionIntensity.FULL,
                    )
                )
            )
        )
        assertEquals(setOf(HookEvent.USER_PROMPT_SUBMIT), e.requiredHookEvents())
    }

    @Test
    fun `injection is produced for Claude Code target when enabled`() {
        val e = engine(
            CapabilitySettings(
                modules = mapOf(
                    ResponseCompressionCapability.id to CapabilityModuleSettings(
                        enabled = true,
                        intensity = CompressionIntensity.ULTRA,
                    )
                )
            )
        )
        val text = e.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.CLAUDE_CODE)
        assertTrue(text.contains("Telegraphic"))
        assertTrue(text.contains(ResponseCompressionCapability.GOLDEN_RULE))
    }

    @Test
    fun `injection is produced for Copilot CLI target when enabled`() {
        val e = engine(
            CapabilitySettings(
                modules = mapOf(
                    ResponseCompressionCapability.id to CapabilityModuleSettings(enabled = true)
                )
            )
        )
        val text = e.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.COPILOT_CLI)
        assertFalse(text.isBlank())
    }
}
