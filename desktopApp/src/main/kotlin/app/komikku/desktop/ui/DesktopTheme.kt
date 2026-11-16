package app.komikku.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.theme.TachiyomiTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The app theme on desktop: the shared [TachiyomiTheme] (app theme, AMOLED, custom / cover-based
 * colors from `UiPreferences`) with the light / dark choice of `UiPreferences.themeMode()` applied
 * through Compose's system theme (Android applies it through `AppCompatDelegate`).
 *
 * `LocalSystemTheme` is the only hook that changes what `isSystemInDarkTheme()` (used by the shared
 * theme) returns on desktop; CMP 1.12 deprecates it ("made public by mistake"). When it is removed,
 * forced light / dark needs a desktop theme entry point that takes `isDark` (PROGRESS.md, 4.3).
 */
@Suppress("DEPRECATION")
@OptIn(InternalComposeUiApi::class)
@Composable
fun DesktopTheme(
    uiPreferences: UiPreferences = remember { Injekt.get() },
    content: @Composable () -> Unit,
) {
    val themeMode by uiPreferences.themeMode().collectAsState()
    val appTheme by uiPreferences.appTheme().collectAsState()
    val amoled by uiPreferences.themeDarkAmoled().collectAsState()
    val systemTheme = when (themeMode) {
        ThemeMode.LIGHT -> SystemTheme.Light
        ThemeMode.DARK -> SystemTheme.Dark
        ThemeMode.SYSTEM -> LocalSystemTheme.current
    }
    CompositionLocalProvider(LocalSystemTheme provides systemTheme) {
        TachiyomiTheme(appTheme = appTheme, amoled = amoled, content = content)
    }
}
