package app.komikku.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.komikku.shared.jssource.JsSourceLabel
import app.komikku.shared.jssource.JsSourcesScreenModel
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/** Browse home: Sources / Extensions tabs, like Android's Browse screen, and the JavaScript sources (I4). */
data object BrowseHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val extensionsModel = rememberScreenModel { ExtensionsScreenModel() }
        val jsSourcesModel = rememberScreenModel { JsSourcesScreenModel() }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selected) {
                Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text(stringResource(MR.strings.label_sources)) })
                Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text(stringResource(MR.strings.label_extensions)) })
                Tab(selected = selected == 2, onClick = { selected = 2 }, text = { Text(stringResource(KMR.strings.js_sources)) })
            }
            when (selected) {
                0 -> SourcesPane()
                1 -> ExtensionsPane(extensionsModel)
                else -> JsSourcesPane(jsSourcesModel)
            }
        }
    }
}

/** The sources of the desktop `SourceManager` by language; click opens the catalogue. */
@Composable
private fun SourcesPane() {
    val navigator = LocalNavigator.currentOrThrow
    val sourceRepository = remember { Injekt.get<SourceRepository>() }
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val sources by remember(sourceRepository) { sourceRepository.getSources() }.collectAsState(initial = null)
    val current = sources
    when {
        current == null -> LoadingScreen()
        current.isEmpty() -> EmptyScreen(stringRes = KMR.strings.desktop_no_sources)
        else -> {
            val byLanguage = remember(current) {
                current
                    .sortedWith(compareBy<Source> { it.lang }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .groupBy { it.lang }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                byLanguage.forEach { (lang, langSources) ->
                    item(key = "header-$lang") { ListGroupHeader(text = languageName(lang)) }
                    items(items = langSources, key = { it.id }) { source ->
                        val live = sourceManager.get(source.id)
                        val configurable = live is ConfigurableSource
                        ListItem(
                            modifier = Modifier.clickable { navigator.push(CatalogueScreen(source.id)) },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = source.name)
                                    if (live is MangayomiJsSource) JsSourceLabel()
                                }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (source.isLocal()) Icons.Outlined.Folder else Icons.Outlined.Public,
                                    contentDescription = null,
                                )
                            },
                            trailingContent = if (configurable) {
                                {
                                    IconButton(onClick = { navigator.push(SourcePreferencesScreen(source.id)) }) {
                                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun languageName(lang: String): String =
    if (lang.isBlank() || lang == "other" || lang == "all") {
        lang
    } else {
        Locale.forLanguageTag(lang).let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase() }
    }
