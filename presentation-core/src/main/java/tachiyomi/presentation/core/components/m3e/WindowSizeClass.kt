package tachiyomi.presentation.core.components.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

/**
 * The window size class of the window that shows the content below it.
 *
 * `MainActivity` provides the value from `currentWindowAdaptiveInfo()`, which also sees the large
 * and extra-large width classes. A preview or a test that provides nothing reads a value computed
 * from the configuration, so a caller never checks for a null.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass?> { null }

/** The window size class of the current window. */
@Composable
@ReadOnlyComposable
fun currentWindowSizeClass(): WindowSizeClass {
    LocalWindowSizeClass.current?.let { return it }

    val configuration = LocalConfiguration.current
    return WindowSizeClass.BREAKPOINTS_V1
        .computeWindowSizeClass(configuration.screenWidthDp, configuration.screenHeightDp)
}

/**
 * True when the window is at or above the medium width class, 600 dp.
 *
 * Material 3 replaces the navigation bar with a rail from this width class up.
 */
@Composable
@ReadOnlyComposable
fun useNavigationRail(): Boolean = currentWindowSizeClass()
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
