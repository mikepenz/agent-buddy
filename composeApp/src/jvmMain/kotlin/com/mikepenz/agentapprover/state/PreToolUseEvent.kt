package com.mikepenz.agentapprover.state

import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.datetime.Instant

data class PreToolUseEvent(
    val request: ApprovalRequest,
    val hits: List<ProtectionHit>,
    val conclusion: ProtectionLogConclusion,
    val timestamp: Instant,
)

enum class ProtectionLogConclusion {
    PASS,
    AUTO_BLOCKED,
    ASK,
    LOGGED,
}

fun conclusionFromHits(hits: List<ProtectionHit>): ProtectionLogConclusion {
    if (hits.isEmpty()) return ProtectionLogConclusion.PASS
    val highest = hits.minByOrNull { it.mode.ordinal }?.mode ?: return ProtectionLogConclusion.PASS
    return when (highest) {
        ProtectionMode.AUTO_BLOCK -> ProtectionLogConclusion.AUTO_BLOCKED
        ProtectionMode.ASK_AUTO_BLOCK, ProtectionMode.ASK -> ProtectionLogConclusion.ASK
        ProtectionMode.LOG_ONLY -> ProtectionLogConclusion.LOGGED
        ProtectionMode.DISABLED -> ProtectionLogConclusion.PASS
    }
}
