package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.komikku.shared.jssource.JsSourcesScreenModel
import app.komikku.shared.jssource.MangayomiSourceStore
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * Browse home: Sources / Feed (P13) / Extensions tabs, like Android's Browse screen, and the JavaScript sources (I4).
 * Like Android's `BrowseTab`, the Feed tab follows "hide feed tab" and "feed tab in front" (`UiPreferences`).
 */
data object BrowseHomeScreen : Screen() {

    private enum class BrowseTab { Sources, Feed, Extensions, JsSources }

    @Composable
    override fun Content() {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val hideFeedTab by uiPreferences.hideFeedTab().collectAsState()
        val feedTabInFront by uiPreferences.feedTabInFront().collectAsState()
        val extensionsModel = rememberScreenModel { ExtensionsScreenModel() }
        val sourcesModel = rememberScreenModel { SourcesPaneModel() }
        val tabs = when {
            hideFeedTab -> listOf(BrowseTab.Sources, BrowseTab.Extensions, BrowseTab.JsSources)
            feedTabInFront -> listOf(BrowseTab.Feed, BrowseTab.Sources, BrowseTab.Extensions, BrowseTab.JsSources)
            else -> listOf(BrowseTab.Sources, BrowseTab.Feed, BrowseTab.Extensions, BrowseTab.JsSources)
        }
        // The selected tab by name: it stays selected when the Feed tab moves; the first tab when it is hidden
        var selectedName by rememberSaveable { mutableStateOf(tabs.first().name) }
        val selected = tabs.firstOrNull { it.name == selectedName } ?: tabs.first()
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(selected)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == selected,
                        onClick = { selectedName = tab.name },
                        text = {
                            Text(
                                when (tab) {
                                    BrowseTab.Sources -> stringResource(MR.strings.label_sources)
                                    BrowseTab.Feed -> stringResource(SYMR.strings.feed)
                                    BrowseTab.Extensions -> stringResource(MR.strings.label_extensions)
                                    BrowseTab.JsSources -> stringResource(KMR.strings.js_sources)
                                },
                            )
                        },
                    )
                }
            }
            when (selected) {
                BrowseTab.Sources -> SourcesPane(sourcesModel)
                BrowseTab.Feed -> FeedTab()
                BrowseTab.Extensions -> ExtensionsPane(extensionsModel)
                BrowseTab.JsSources -> JsSourcesTab()
            }
        }
    }
}

/**
 * The Feed tab. Its model (which loads every feed row when created) is only created when the tab is first shown; it
 * stays with the Browse screen afterwards.
 */
@Composable
private fun BrowseHomeScreen.FeedTab() {
    val model = rememberScreenModel { FeedPaneModel() }
    FeedPane(model)
}

/**
 * The JavaScript sources tab. Its model (which reads every added repository when created) is only created when the tab
 * is opened, and only when the store could be built.
 */
@Composable
private fun BrowseHomeScreen.JsSourcesTab() {
    val store = remember { MangayomiSourceStore.getOrNull() }
    if (store == null) {
        EmptyScreen(stringRes = KMR.strings.js_sources_unavailable)
        return
    }
    val model = rememberScreenModel { JsSourcesScreenModel(store) }
    JsSourcesPane(model, store)
}

internal fun languageName(lang: String): String =
    if (lang.isBlank() || lang == "other" || lang == "all") {
        lang
    } else {
        Locale.forLanguageTag(lang).let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase() }
    }
