package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
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

/**
 * Browse destination: the sources of the desktop `SourceManager` (the local source until extensions
 * run on desktop, Phase 5), grouped by language. Browsing a catalogue comes with Phase 6.1.
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
        val sourceRepository = remember { Injekt.get<SourceRepository>() }
        val sources by remember(sourceRepository) { sourceRepository.getSources() }.collectAsState(initial = null)
        val current = sources
        when {
            current == null -> LoadingScreen()
            current.isEmpty() -> EmptyScreen(stringRes = KMR.strings.desktop_no_sources)
            else -> SourceList(current)
        }
    }
}

@Composable
private fun SourceList(sources: List<Source>) {
    val byLanguage = remember(sources) {
        sources
            .sortedWith(compareBy<Source> { it.lang }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .groupBy { it.lang }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        byLanguage.forEach { (lang, langSources) ->
            item(key = "header-$lang") {
                ListGroupHeader(text = languageName(lang))
            }
            items(items = langSources, key = { it.id }) { source ->
                ListItem(
                    headlineContent = { Text(text = source.name) },
                    leadingContent = {
                        Icon(
                            imageVector = if (source.isLocal()) Icons.Outlined.Folder else Icons.Outlined.Public,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

private fun languageName(lang: String): String =
    if (lang.isBlank() || lang == "other" || lang == "all") {
        lang
    } else {
        Locale.forLanguageTag(lang).let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase() }
    }
