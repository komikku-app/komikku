package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.core.prefs.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.storage.DiskUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseScreen(
    private val toExtensions: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { BrowseScreenModel() }

        // Hoisted for extensions tab's search bar
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsQuery by extensionsScreenModel.query.collectAsState()

        TabbedScreen(
            titleRes = R.string.browse,
            // SY -->
            tabs = if (screenModel.feedTabInFront) {
                listOf(
                    feedTab(),
                    sourcesTab(),
                    extensionsTab(extensionsScreenModel),
                    migrateSourceTab(),
                )
            } else {
                listOf(
                    sourcesTab(),
                    feedTab(),
                    extensionsTab(extensionsScreenModel),
                    migrateSourceTab(),
                )
            },
            startIndex = 2.takeIf { toExtensions },
            // SY <--
            searchQuery = extensionsQuery,
            onChangeSearchQuery = extensionsScreenModel::search,
            incognitoMode = screenModel.isIncognitoMode,
            downloadedOnlyMode = screenModel.isDownloadOnly,
        )

        // For local source
        DiskUtil.RequestStoragePermission()

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private class BrowseScreenModel(
    preferences: BasePreferences = Injekt.get(),
    // SY -->
    uiPreferences: UiPreferences = Injekt.get(),
    // SY <--
) : ScreenModel {
    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState(coroutineScope)
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState(coroutineScope)

    // SY -->
    val feedTabInFront = uiPreferences.feedTabInFront().get()
    // SY <--
}
