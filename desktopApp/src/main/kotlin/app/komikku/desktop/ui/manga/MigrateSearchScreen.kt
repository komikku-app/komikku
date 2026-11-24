package app.komikku.desktop.ui.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.komikku.desktop.ui.browse.CatalogueScreen
import app.komikku.desktop.ui.browse.GlobalSearchContent
import app.komikku.desktop.ui.browse.GlobalSearchToolbar
import app.komikku.desktop.ui.browse.globalSearchRowTitle
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
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P18: Android's single-entry migration from the manga details (`MigrateSearchScreen` with `MigrateSearchScreenModel`):
 * the entry's title searched in the migration sources like the global search (the current source's row marked "▶"), a
 * result opens Mihon's "what to include" dialog (the borrowed `MigrateMangaDialog`: chapters' read state and history,
 * categories, tracks, custom cover, notes, delete downloads, extras by `MigrationFlag`; Copy keeps the old entry in the
 * library, Migrate replaces it) and after it the new entry's details replace this screen, like Android. A row header
 * opens that source's catalogue with the query (Android: its own source search screen for the migration).
 *
 * Not built: Android's migration list (several entries, automatic matching), its configuration screen (sources and
 * flags), the pick from a source's own search.
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
        val state by model.state.collectAsState()
        // Like the global search: the pinned / all filter reaches the state from its preference after the first search
        LaunchedEffect(state.sourceFilter) { model.search() }
        val hasPinnedSources = remember(state.sourceFilter) { model.hasPinnedSources() }
        val extensions by remember { SourceIcons.installedExtensions() }.collectAsState()
        val fromSource = state.from?.source
        val context = LocalContext.current
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
                onClickItem = { if (it.id != mangaId) model.setMigrateDialog(mangaId, it) },
                onLongClickItem = { navigator.push(MangaDetailsScreen(it.id)) },
                selection = emptyList(),
                rowTitle = { source ->
                    globalSearchRowTitle(source, extensions).let { if (source.id == fromSource) "▶ $it" else it }
                },
            )
        }
        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.Migrate -> MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = { navigator.push(MangaDetailsScreen(dialog.target.id)) },
                onDismissRequest = model::clearDialog,
                onComplete = { navigator.replace(MangaDetailsScreen(dialog.target.id)) },
                // Review fix (P18): the borrowed use case reports a failed migration; stay here and say so
                onFailure = {
                    model.clearDialog()
                    context.toast(context.stringResource(KMR.strings.desktop_migration_failed, dialog.current.title))
                },
            )
            null -> {}
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
 * their order) searched. Desktop deviation: Android's migration configuration screen fills those sources before a
 * search; the desktop has none yet, so without them the global search's sources are searched.
 */
class MigrateSearchScreenModel(
    val mangaId: Long,
    getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : SearchScreenModel() {

    private val migrationSources by lazy { sourcePreferences.migrationSources().get() }

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

    override fun getEnabledSources(): List<Source> {
        return migrationSources.mapNotNull { sourceManager.get(it) }.ifEmpty { super.getEnabledSources() }
    }
}
