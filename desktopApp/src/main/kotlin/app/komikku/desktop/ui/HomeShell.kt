package app.komikku.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.komikku.desktop.ui.tabs.BrowseTab
import app.komikku.desktop.ui.tabs.HistoryTab
import app.komikku.desktop.ui.tabs.LibraryTab
import app.komikku.desktop.ui.tabs.MoreTab
import app.komikku.desktop.ui.tabs.UpdatesTab
import app.komikku.desktop.window.DesktopShortcuts
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import tachiyomi.presentation.core.components.material.NavigationRail

/** Navigation destinations in rail order (also the order of the Ctrl/Cmd+1…5 shortcuts). */
val homeTabs: List<Tab> = listOf(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, MoreTab)

/**
 * The desktop home shell: a Material 3 navigation rail (Library / Updates / History / Browse / More)
 * next to the current destination, each destination a Voyager [Tab]. The desktop counterpart of the
 * Android `HomeScreen` in its tablet (rail) layout.
 */
@Composable
fun HomeShell(shortcuts: DesktopShortcuts? = null) {
    TabNavigator(LibraryTab) { tabNavigator ->
        if (shortcuts != null) {
            DisposableEffect(shortcuts, tabNavigator) {
                shortcuts.handler = { action ->
                    when (action) {
                        is DesktopShortcuts.Action.SelectTab -> homeTabs.getOrNull(action.index)?.let { tabNavigator.current = it }
                    }
                }
                onDispose { shortcuts.handler = null }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    homeTabs.forEach { tab ->
                        val options = tab.options
                        NavigationRailItem(
                            selected = tabNavigator.current.key == tab.key,
                            onClick = { tabNavigator.current = tab },
                            icon = { options.icon?.let { Icon(painter = it, contentDescription = options.title) } },
                            label = { Text(text = options.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    CurrentTab()
                }
            }
        }
    }
}
