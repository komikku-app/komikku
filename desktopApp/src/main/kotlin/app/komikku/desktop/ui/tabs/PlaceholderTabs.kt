package app.komikku.desktop.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/** A destination whose desktop screen does not exist yet (Phase 6): title, icon, "not available yet". */
abstract class PlaceholderTab(
    private val index: UShort,
    private val title: StringResource,
    private val icon: ImageVector,
) : Tab {

    override val key: ScreenKey get() = "desktop-tab-$index"

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = index,
            title = stringResource(title),
            icon = rememberVectorPainter(icon),
        )

    @Composable
    override fun Content() {
        EmptyScreen(stringRes = KMR.strings.desktop_not_available_yet)
    }
}

data object UpdatesTab : PlaceholderTab(1u, MR.strings.label_recent_updates, Icons.Outlined.NewReleases)

data object HistoryTab : PlaceholderTab(2u, MR.strings.label_recent_manga, Icons.Outlined.History)

data object MoreTab : PlaceholderTab(4u, MR.strings.label_more, Icons.Outlined.MoreHoriz)
