package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis

interface RiskAnalyzer {
    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis>
    fun shutdown() {}

    /**
     * Free-form prompt → text completion path. Used by the Optimization
     * Insights feature to get a tailored suggestion from the same backend
     * the user already configured for risk analysis, without forcing the
     * caller through the [HookInput] shape (which is rigidly typed for
     * tool-call risk classification).
     *
     * Default returns a failure so analyzers can be added incrementally —
     * implementations override when the backend supports a generic prompt.
     */
    suspend fun analyzeText(systemPrompt: String, userPrompt: String): Result<String> =
        Result.failure(UnsupportedOperationException("analyzeText not implemented for ${this::class.simpleName}"))
}
