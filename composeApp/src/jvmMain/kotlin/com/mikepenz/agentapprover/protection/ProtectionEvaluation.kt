package com.mikepenz.agentapprover.protection

import com.mikepenz.agentapprover.model.ProtectionMode

data class ProtectionEvaluation(
    val moduleId: String,
    val moduleName: String,
    val mode: ProtectionMode,
    val applicable: Boolean,
    val enabled: Boolean,
    val ruleResults: List<RuleEvalResult>,
)

data class RuleEvalResult(
    val ruleId: String,
    val ruleName: String,
    val enabled: Boolean,
    val matched: Boolean,
    val message: String?,
)
