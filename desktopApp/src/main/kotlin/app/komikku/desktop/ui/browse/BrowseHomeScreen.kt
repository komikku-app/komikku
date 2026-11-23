package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.komikku.shared.jssource.JsSourcesScreenModel
import app.komikku.shared.jssource.MangayomiSourceStore
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import java.util.Locale

/** Browse home: Sources / Extensions tabs, like Android's Browse screen, and the JavaScript sources (I4). */
data object BrowseHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val extensionsModel = rememberScreenModel { ExtensionsScreenModel() }
        val sourcesModel = rememberScreenModel { SourcesPaneModel() }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selected) {
                Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text(stringResource(MR.strings.label_sources)) })
                Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text(stringResource(MR.strings.label_extensions)) })
                Tab(selected = selected == 2, onClick = { selected = 2 }, text = { Text(stringResource(KMR.strings.js_sources)) })
            }
            when (selected) {
                0 -> SourcesPane(sourcesModel)
                1 -> ExtensionsPane(extensionsModel)
                else -> JsSourcesTab()
            }
        }
    }
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
