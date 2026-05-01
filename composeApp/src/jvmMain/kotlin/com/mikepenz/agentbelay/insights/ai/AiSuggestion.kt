package com.mikepenz.agentbelay.insights.ai

/**
 * Parsed result of one AI elevation request. Built from the model's reply
 * to the [SystemPrompts.DEFAULT] template — falls back to raw body when
 * parsing fails so the user still sees something actionable.
 */
data class AiSuggestion(
    val title: String,
    val body: String,
    val action: String? = null,
    val rawText: String,
)
