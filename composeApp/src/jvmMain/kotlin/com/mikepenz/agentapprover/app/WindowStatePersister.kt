package com.mikepenz.agentapprover.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.mikepenz.agentapprover.state.AppStateManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

private const val DEFAULT_WINDOW_WIDTH = 420
private const val DEFAULT_WINDOW_HEIGHT = 480
private const val WINDOW_STATE_DEBOUNCE_MS = 500L

/**
 * Compose helper that builds a [WindowState] from the position/size already
 * loaded into [AppStateManager]'s in-memory settings, and registers a
 * debounced effect to persist any changes back through
 * [AppStateManager.updateSettings].
 *
 * Going through `stateManager` (rather than reading and writing
 * `SettingsStorage` directly) avoids two race conditions:
 *
 * 1. Reading from disk inside `remember { }` blocks the UI thread on a
 *    synchronous file parse during composition. The in-memory state is
 *    already populated at app startup so we just snapshot it.
 * 2. The classic read-modify-write race: if any other writer (e.g. Away
 *    Mode tray toggle, theme change) saves between our load and our save,
 *    their change is overwritten. Composing the window-state copy against
 *    the latest in-memory state, then routing through
 *    [AppStateManager.updateSettings] (which is `synchronized` and uses
 *    the StateFlow as the source of truth), eliminates the race.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedWindowState(stateManager: AppStateManager): WindowState {
    val windowState = remember {
        val settings = stateManager.state.value.settings
        val position = if (settings.windowX != null && settings.windowY != null) {
            WindowPosition.Absolute(settings.windowX.dp, settings.windowY.dp)
        } else {
            WindowPosition.PlatformDefault
        }
        val size = DpSize(
            width = (settings.windowWidth ?: DEFAULT_WINDOW_WIDTH).dp,
            height = (settings.windowHeight ?: DEFAULT_WINDOW_HEIGHT).dp,
        )
        WindowState(position = position, size = size)
    }

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .distinctUntilChanged()
            .debounce(WINDOW_STATE_DEBOUNCE_MS)
            .collect { (pos, size) ->
                if (pos is WindowPosition.Absolute) {
                    val current = stateManager.state.value.settings
                    stateManager.updateSettings(
                        current.copy(
                            windowX = pos.x.value.toInt(),
                            windowY = pos.y.value.toInt(),
                            windowWidth = size.width.value.toInt(),
                            windowHeight = size.height.value.toInt(),
                        ),
                    )
                }
            }
    }

    return windowState
}
