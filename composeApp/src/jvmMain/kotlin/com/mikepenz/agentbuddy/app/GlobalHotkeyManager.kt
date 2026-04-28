package com.mikepenz.agentbuddy.app

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.GlobalHotkey
import com.mikepenz.agentbuddy.model.HotkeyModifier
import com.mikepenz.agentbuddy.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.nucleus.globalhotkey.GlobalHotKeyManager
import io.github.kdroidfilter.nucleus.globalhotkey.HotKeyListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped wrapper around Nucleus' [GlobalHotKeyManager] that keeps the OS-
 * level shortcuts in sync with [com.mikepenz.agentbuddy.model.AppSettings].
 *
 * Two shortcuts are supported: approve-oldest and deny-oldest. When either
 * fires we resolve the **oldest** entry in [AppStateManager.state]'s
 * `pendingApprovals` list (which is prepended on insert, so the oldest is at
 * the tail). If no requests are pending the keystroke is a no-op.
 *
 * Lifecycle:
 *  - [start] is idempotent. It calls [GlobalHotKeyManager.initialize] on the
 *    first invocation, then begins observing settings.
 *  - [shutdown] cancels the observer, unregisters every active hotkey and
 *    shuts down the underlying Nucleus manager. Idempotent.
 *
 * The Nucleus library decides per-platform whether the OS exposes a
 * registration API (Windows / macOS / Linux). When unavailable, [start] just
 * leaves the listeners unregistered — the in-app behaviour is otherwise
 * unaffected.
 */
@SingleIn(AppScope::class)
@Inject
class GlobalHotkeyManager(
    private val stateManager: AppStateManager,
    private val environment: AppEnvironment,
) {
    private val log = Logger.withTag("GlobalHotkeyManager")

    private var collectorJob: Job? = null
    private var initialized = false

    private var approveHandle: Long = 0L
    private var denyHandle: Long = 0L
    private var approveBound: GlobalHotkey? = null
    private var denyBound: GlobalHotkey? = null

    fun start() {
        if (collectorJob != null) return
        if (!initialized) {
            initialized = GlobalHotKeyManager.initialize()
            if (!initialized) {
                log.w {
                    "Global hotkey manager unavailable on this platform: " +
                        (GlobalHotKeyManager.lastError ?: "unknown")
                }
            }
        }
        collectorJob = environment.appScope.launch {
            stateManager.state
                .map { it.settings.approveOldestHotkey to it.settings.denyOldestHotkey }
                .distinctUntilChanged()
                .collect { (approve, deny) ->
                    syncBinding(role = HotkeyRole.APPROVE, target = approve)
                    syncBinding(role = HotkeyRole.DENY, target = deny)
                }
        }
    }

    fun shutdown() {
        collectorJob?.cancel()
        collectorJob = null
        if (approveHandle != 0L) {
            GlobalHotKeyManager.unregister(approveHandle)
            approveHandle = 0L
            approveBound = null
        }
        if (denyHandle != 0L) {
            GlobalHotKeyManager.unregister(denyHandle)
            denyHandle = 0L
            denyBound = null
        }
        if (initialized) {
            runCatching { GlobalHotKeyManager.shutdown() }
            initialized = false
        }
    }

    private fun syncBinding(role: HotkeyRole, target: GlobalHotkey?) {
        val (boundRef, handleRef) = when (role) {
            HotkeyRole.APPROVE -> approveBound to approveHandle
            HotkeyRole.DENY -> denyBound to denyHandle
        }
        if (boundRef == target) return  // already up to date
        // Tear down the old registration if any.
        if (handleRef != 0L) {
            GlobalHotKeyManager.unregister(handleRef)
            setHandle(role, 0L, null)
        }
        if (target == null || !initialized) return
        val nucleusModifiers = target.modifiers.combinedNativeFlag()
        val handle = runCatching {
            GlobalHotKeyManager.register(
                nucleusModifiers,
                target.keyCode,
                HotKeyListener { _, _ -> onHotkeyFired(role) },
            )
        }.getOrElse {
            log.e(it) { "Failed to register $role hotkey: $target" }
            return
        }
        if (handle == 0L) {
            log.w { "Nucleus refused to register $role hotkey ($target): ${GlobalHotKeyManager.lastError}" }
            return
        }
        setHandle(role, handle, target)
        log.i { "Registered $role hotkey: $target (handle=$handle)" }
    }

    private fun setHandle(role: HotkeyRole, handle: Long, bound: GlobalHotkey?) {
        when (role) {
            HotkeyRole.APPROVE -> {
                approveHandle = handle
                approveBound = bound
            }
            HotkeyRole.DENY -> {
                denyHandle = handle
                denyBound = bound
            }
        }
    }

    private fun onHotkeyFired(role: HotkeyRole) {
        // Pending list is newest-first (see AppStateManager.addPending) — the
        // last entry is the oldest unresolved request, which is what the user
        // most likely intends to action with a "next item" hotkey.
        val oldest = stateManager.state.value.pendingApprovals.lastOrNull()
        if (oldest == null) {
            log.d { "Hotkey $role fired but no pending approvals" }
            return
        }
        val (decision, feedback) = when (role) {
            HotkeyRole.APPROVE -> Decision.APPROVED to "Approved via global hotkey"
            HotkeyRole.DENY -> Decision.DENIED to "Denied via global hotkey"
        }
        stateManager.resolve(
            requestId = oldest.id,
            decision = decision,
            feedback = feedback,
            riskAnalysis = null,
            rawResponseJson = null,
        )
        log.i { "Hotkey $role resolved oldest pending ${oldest.id}" }
    }

    private enum class HotkeyRole { APPROVE, DENY }
}

private fun Set<HotkeyModifier>.combinedNativeFlag(): Int =
    fold(0) { acc, mod -> acc or mod.nativeFlag() }

// Mirrors the bit values the Nucleus library uses internally
// (`HotKeyModifier.nativeFlag`) — copied here because the property is
// `internal` to the global-hotkey module and not callable from outside.
// These match Windows' MOD_* constants and the Nucleus library normalises
// them per-platform before reaching the native bridge.
private fun HotkeyModifier.nativeFlag(): Int = when (this) {
    HotkeyModifier.ALT -> 1
    HotkeyModifier.CONTROL -> 2
    HotkeyModifier.SHIFT -> 4
    HotkeyModifier.META -> 8
}
