package app.komikku.desktop.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.ui.browse.CatalogueScreen
import app.komikku.desktop.ui.browse.GlobalSearchContent
import app.komikku.desktop.ui.browse.GlobalSearchToolbar
import app.komikku.desktop.ui.browse.globalSearchRowTitle
import app.komikku.desktop.ui.migration.MigrationConfigScreen
import app.komikku.desktop.ui.migration.MigrationListScreen
import app.komikku.desktop.ui.tabs.SourceIcons
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P18 / P19: Android's migration search (`MigrateSearchScreen` with `MigrateSearchScreenModel`), opened from the migration
 * list ([MigrationListScreen]: its "search manually", or at once for one entry): the entry's title searched in the
 * migration sources like the global search (the current source's row marked "▶", the entry itself left out); a pick
 * becomes that entry's match in the list and the list is shown again, like Android. A row header opens that source's
 * catalogue with the query (Android: its own source search screen for the migration). Not built: the pick from a
 * source's own search.
 *
 * Review fix (P19): the screen is only reached from the list, so Android's own "what to include" dialog for a pick
 * without a list is gone; when none of the migration sources is installed any more, the search says so and offers the
 * migration configuration for the list's entries (Android shows an empty search).
 */
class MigrateSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val sourcesLoaded by sourceManager.isInitialized.collectAsState()
        if (!sourcesLoaded) {
            LoadingScreen()
            return
        }
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MigrateSearchScreenModel(mangaId) }
        if (!model.hasMigrationSources) {
            NoMigrationSources(
                onBack = navigator::pop,
                onChooseSources = {
                    // The list's entries again from the configuration (the list is below this search)
                    val list = navigator.items.filterIsInstance<MigrationListScreen>().lastOrNull()
                    if (list == null) {
                        navigator.replace(MigrationConfigScreen(mangaId))
                    } else {
                        navigator.popUntil { it === list }
                        navigator.replace(MigrationConfigScreen(list.mangaIds))
                    }
                },
            )
            return
        }
        val state by model.state.collectAsState()
        // Like the global search: the pinned / all filter reaches the state from its preference after the first search
        LaunchedEffect(state.sourceFilter) { model.search() }
        val hasPinnedSources = remember(state.sourceFilter) { model.hasPinnedSources() }
        val extensions by remember { SourceIcons.installedExtensions() }.collectAsState()
        val fromSource = state.from?.source
        // Review fix (P18): the entry itself is no migration target (its own source's row lists it)
        val shown = remember(state) { state.withoutEntry(mangaId) }
        Column(modifier = Modifier.fillMaxSize().testTag("migrate-search")) {
            GlobalSearchToolbar(
                state = shown,
                hasPinnedSources = hasPinnedSources,
                onBack = navigator::pop,
                onChangeSearchQuery = model::updateSearchQuery,
                onSearch = model::search,
                onChangeSearchFilter = model::setSourceFilter,
                onToggleResults = model::toggleFilterResults,
                bulkSelectionButton = {},
                // Like Android's migration search: its sources are the migration sources, no pinned / all filter
                hideSourceFilter = true,
            )
            GlobalSearchContent(
                state = shown,
                getManga = { model.getManga(it) },
                onClickSource = { navigator.push(CatalogueScreen(it.id, initialQuery = state.searchQuery)) },
                onClickItem = { target ->
                    if (target.id == mangaId) return@GlobalSearchContent
                    // P19, like Android: the pick becomes the entry's match in the migration list below
                    val list = navigator.items.filterIsInstance<MigrationListScreen>().lastOrNull() ?: return@GlobalSearchContent
                    list.addMatchOverride(current = mangaId, target = target.id)
                    navigator.popUntil { it === list }
                },
                onLongClickItem = { navigator.push(MangaDetailsScreen(it.id)) },
                selection = emptyList(),
                rowTitle = { source ->
                    globalSearchRowTitle(source, extensions).let { if (source.id == fromSource) "▶ $it" else it }
                },
            )
        }
    }
}

/** Review fix (P19): none of the migration sources is installed (an extension removed after configuring). */
@Composable
private fun NoMigrationSources(onBack: () -> Unit, onChooseSources: () -> Unit) {
    BrowseScaffold(title = stringResource(MR.strings.action_migrate), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("migrate-search-no-sources"),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(KMR.strings.desktop_migration_no_sources),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onChooseSources, modifier = Modifier.testTag("migrate-search-choose-sources")) {
                Text(stringResource(SYMR.strings.select_sources))
            }
        }
    }
}

/** The search's state without [mangaId] in the results (P18 review fix: an entry cannot be migrated onto itself). */
internal fun SearchScreenModel.State.withoutEntry(mangaId: Long): SearchScreenModel.State {
    if (items.values.none { it is SearchItemResult.Success && it.result.any { manga -> manga.id == mangaId } }) return this
    return copy(
        items = items.mapValues { (_, result) ->
            if (result is SearchItemResult.Success) SearchItemResult.Success(result.result.filterNot { it.id == mangaId }) else result
        }.toPersistentMap(),
    )
}

/**
 * Android's `MigrateSearchScreenModel`: the entry's title as the query, the migration sources (`migrationSources`, in
 * their order) searched; the migration configuration screen ([app.komikku.desktop.ui.migration.MigrationConfigScreen])
 * fills them before a search is opened.
 */
class MigrateSearchScreenModel(
    val mangaId: Long,
    getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : SearchScreenModel() {

    private val migrationSources by lazy { sourcePreferences.migrationSources().get() }

    /** Review fix (P19): whether any of the migration sources is installed (else the search has no rows at all). */
    val hasMigrationSources: Boolean by lazy { getEnabledSources().isNotEmpty() }

    override val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { migrationSources.indexOf(it.id) },
            { "${it.name.lowercase()} (${it.lang})" },
            { it.id },
        )
    }

    init {
        screenModelScope.launch {
            val manga = getManga.await(mangaId) ?: return@launch
            mutableState.update { it.copy(from = manga, searchQuery = manga.title) }
            search()
        }
        shouldPinnedSourcesHidden()
    }

    // P19: Android's — only the migration sources (the configuration screen sets them before a search is opened)
    override fun getEnabledSources(): List<Source> {
        return migrationSources.mapNotNull { sourceManager.get(it) }
    }
}
