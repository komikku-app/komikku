package app.komikku.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * A [WindowState] restored from [DesktopWindowPreferences] and written back (debounced) whenever the
 * user moves, resizes, maximizes or restores the window. A saved position that is no longer on any
 * screen (e.g. a monitor was unplugged) falls back to the platform default.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedWindowState(preferences: DesktopWindowPreferences): WindowState {
    val state = remember(preferences) {
        val placement = restoreWindowPlacement(
            x = preferences.windowX().get(),
            y = preferences.windowY().get(),
            width = preferences.windowWidth().get(),
            height = preferences.windowHeight().get(),
            screens = screenBounds(),
        )
        WindowState(
            placement = if (preferences.windowMaximized().get()) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = placement.position,
            size = placement.size,
        )
    }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.placement, state.position, state.size) }
            .distinctUntilChanged()
            .debounce(WINDOW_STATE_SAVE_DELAY_MS)
            .collect { (placement, position, size) -> saveWindowState(preferences, placement, position, size) }
    }
    return state
}

/** Restored position (dp) and size (dp) of the main window. */
data class RestoredWindowPlacement(val position: WindowPosition, val size: DpSize)

/**
 * Pure part of the restore: clamps the size to the minimum and drops a saved position whose
 * top-left corner is not inside any of [screens] (screen bounds in dp-equivalent AWT units).
 */
fun restoreWindowPlacement(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    screens: List<Rectangle>,
): RestoredWindowPlacement {
    val size = DpSize(
        width.coerceAtLeast(DesktopWindowPreferences.MIN_WIDTH).dp,
        height.coerceAtLeast(DesktopWindowPreferences.MIN_HEIGHT).dp,
    )
    val hasPosition = x != DesktopWindowPreferences.UNSET_POSITION && y != DesktopWindowPreferences.UNSET_POSITION
    val onScreen = hasPosition && (screens.isEmpty() || screens.any { it.contains(x, y) })
    val position = if (onScreen) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault
    return RestoredWindowPlacement(position, size)
}

private fun saveWindowState(
    preferences: DesktopWindowPreferences,
    placement: WindowPlacement,
    position: WindowPosition,
    size: DpSize,
) {
    preferences.windowMaximized().set(placement == WindowPlacement.Maximized)
    // Keep the last floating bounds so "restore" after a maximized start returns to them.
    if (placement != WindowPlacement.Floating) return
    if (position.isSpecified) {
        preferences.windowX().set(position.x.value.toInt())
        preferences.windowY().set(position.y.value.toInt())
    }
    if (size.isSpecified) {
        preferences.windowWidth().set(size.width.value.toInt())
        preferences.windowHeight().set(size.height.value.toInt())
    }
}

private fun screenBounds(): List<Rectangle> = runCatching {
    if (GraphicsEnvironment.isHeadless()) return@runCatching emptyList()
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
}.getOrDefault(emptyList())

private const val WINDOW_STATE_SAVE_DELAY_MS = 500L
