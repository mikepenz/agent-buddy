package com.mikepenz.agentbelay.ui.detail

/**
 * Payload describing a pop-out detail window. The simple case (read-only
 * markdown view of some tool input) only needs [title] + [content]. Plan
 * reviews additionally pass [approveAction] / [denyAction] / [refineAction]
 * so the popped-out window can dispatch back to the approval queue without
 * forcing the user to return to the main window.
 */
data class PopOutSpec(
    val title: String,
    val content: String,
    val approveAction: (() -> Unit)? = null,
    val denyAction: (() -> Unit)? = null,
    val refineAction: ((String) -> Unit)? = null,
) {
    val hasActions: Boolean
        get() = approveAction != null || denyAction != null || refineAction != null
}
