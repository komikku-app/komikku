package app.komikku.desktop.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.komikku.desktop.ui.browse.BrowseHomeScreen
import app.komikku.desktop.ui.navigation.RegisteredNavigatorHost
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Browse destination: its own Voyager navigator (so the rail stays visible) starting at
 * [BrowseHomeScreen] (Sources / Extensions tabs); sources open a catalogue, manga open a details
 * screen, configurable sources a preferences screen. Escape / the back arrows pop.
 */
data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u,
            title = stringResource(MR.strings.browse),
            icon = rememberVectorPainter(Icons.Outlined.Explore),
        )

    @Composable
    override fun Content() {
        RegisteredNavigatorHost(screen = BrowseHomeScreen)
    }
}
