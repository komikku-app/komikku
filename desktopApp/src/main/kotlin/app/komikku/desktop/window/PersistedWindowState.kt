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
import java.awt.Dimension
import java.awt.Rectangle

/**
 * A [WindowState] restored from [DesktopWindowPreferences] and written back (debounced) whenever the
 * user moves, resizes, maximizes or restores the window. The restored window fits a screen's usable area with its
 * title bar on screen ([restoreWindowPlacement]).
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
            screens = DesktopScreens.usableAreas(),
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
 * Pure part of the restore, over the screens' usable areas ([DesktopScreens.usableAreas], main screen first). The
 * window goes on the screen that holds most of its saved title bar, else on the main screen (a monitor unplugged or
 * smaller since), shrinks to that screen's usable area when bigger (a size saved on a bigger or less scaled display)
 * and moves so the whole window, its title bar included, is inside it. Without a saved position it is centred on the
 * main screen. The size never goes below the window's minimum unless the screen itself is smaller. Without screens
 * (headless) only the minimum applies.
 */
fun restoreWindowPlacement(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    screens: List<Rectangle>,
): RestoredWindowPlacement {
    val wanted = Dimension(
        width.coerceAtLeast(DesktopWindowPreferences.MIN_WIDTH),
        height.coerceAtLeast(DesktopWindowPreferences.MIN_HEIGHT),
    )
    val hasPosition = x != DesktopWindowPreferences.UNSET_POSITION && y != DesktopWindowPreferences.UNSET_POSITION
    if (screens.isEmpty()) {
        val position = if (hasPosition) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault
        return RestoredWindowPlacement(position, DpSize(wanted.width.dp, wanted.height.dp))
    }
    val titleBar = Rectangle(x, y, wanted.width, TITLE_BAR_HEIGHT)
    val screen = screens
        .takeIf { hasPosition }
        ?.map { it to it.intersection(titleBar) }
        ?.filter { (_, shared) -> !shared.isEmpty }
        ?.maxByOrNull { (_, shared) -> shared.width.toLong() * shared.height }
        ?.first
    val area = screen ?: screens.first()
    val fitWidth = wanted.width.coerceAtMost(area.width)
    val fitHeight = wanted.height.coerceAtMost(area.height)
    val left = if (screen != null) x.coerceIn(area.x, area.x + area.width - fitWidth) else area.x + (area.width - fitWidth) / 2
    val top = if (screen != null) y.coerceIn(area.y, area.y + area.height - fitHeight) else area.y + (area.height - fitHeight) / 2
    return RestoredWindowPlacement(WindowPosition(left.dp, top.dp), DpSize(fitWidth.dp, fitHeight.dp))
}

/** The height of the strip at the top of a window that holds its title bar, in dp; the tallest title bar is ~40. */
private const val TITLE_BAR_HEIGHT = 40

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

private const val WINDOW_STATE_SAVE_DELAY_MS = 500L
