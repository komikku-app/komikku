package app.komikku.desktop.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.tabs.SourceIcons
import app.komikku.shared.jssource.JsSourceLabel
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Android's global search on desktop: the borrowed [GlobalSearchScreenModel] / [SearchScreenModel] (enabled languages,
 * disabled and pinned sources from `SourcePreferences`, five searches at a time, pinned-only / all and "has results"
 * filters persisted like on Android) and the borrowed result row items. One row per source with its progress, results
 * or error; the row header opens that source's own search with the query, a result opens the manga details.
 * JavaScript sources take part like any other source (they are in the source manager).
 *
 * P14: Komikku's bulk favourites (selection mode, `BulkFavorite.kt`) and the extension's name after a source's name when
 * they differ (Android's row title).
 *
 * Not on desktop (yet): the single-result shortcut of the extension filter (Android opens the only match of a deep link
 * directly).
 */
class GlobalSearchScreen(private val searchQuery: String = "") : Screen() {

    @Composable
    override fun Content() {
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val sourcesLoaded by sourceManager.isInitialized.collectAsState()
        if (!sourcesLoaded) {
            LoadingScreen()
            return
        }
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { GlobalSearchScreenModel(initialQuery = searchQuery) }
        val state by model.state.collectAsState()
        // The borrowed model reads the pinned / all filter from its preference flow, after its first search (opened
        // with a query) and after a chip click: search again once the filter in the state changes. `search()` skips a
        // query and filter it has already searched, so this does not search twice.
        LaunchedEffect(state.sourceFilter) { model.search() }
        val hasPinnedSources = remember(state.sourceFilter) { model.hasPinnedSources() }
        val bulkModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkState by bulkModel.state.collectAsState()
        val bulkClicks = BulkClicks(bulkModel, bulkState.selectionMode) { navigator.push(MangaDetailsScreen(it.id)) }
        val extensions by remember { SourceIcons.installedExtensions() }.collectAsState()
        Column(modifier = Modifier.fillMaxSize()) {
            if (bulkState.selectionMode) {
                // Android's BulkSelectionToolbar in place of the search toolbar
                val results = { state.filteredItems.values.filterIsInstance<SearchItemResult.Success>().flatMap { it.result } }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag("bulk-toolbar"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { bulkModel.toggleSelectionMode(false) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
                    }
                    Text("${bulkState.selection.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    BulkSelectionActions(
                        state = bulkState,
                        onSelectAll = { results().forEach(bulkModel::select) },
                        onReverseSelection = { bulkModel.reverseSelection(results()) },
                        onAddToLibrary = { bulkModel.addFavorite() },
                    )
                }
                HorizontalDivider()
            } else {
                GlobalSearchToolbar(
                    state = state,
                    hasPinnedSources = hasPinnedSources,
                    onBack = navigator::pop,
                    onChangeSearchQuery = model::updateSearchQuery,
                    onSearch = model::search,
                    onChangeSearchFilter = model::setSourceFilter,
                    onToggleResults = model::toggleFilterResults,
                    bulkSelectionButton = {
                        BulkSelectionButton(isRunning = bulkState.isRunning, onClick = { bulkModel.toggleSelectionMode() })
                    },
                )
            }
            GlobalSearchContent(
                state = state,
                getManga = { model.getManga(it) },
                onClickSource = { navigator.push(CatalogueScreen(it.id, initialQuery = state.searchQuery)) },
                onClickItem = bulkClicks::onClick,
                onLongClickItem = bulkClicks::onLongClick,
                selection = bulkState.selection,
                rowTitle = { globalSearchRowTitle(it, extensions) },
            )
        }
        BulkFavoriteDialogs(bulkModel, bulkState.dialog)
    }
}

/**
 * The title of a source's row, Android's (KMK): the source's name, followed by its extension's name when that differs
 * (e.g. a multi-source extension).
 */
internal fun globalSearchRowTitle(source: Source, extensions: List<Extension.Installed>): String {
    val extension = extensions.find { extension -> extension.sources.any { it.id == source.id } }
    return source.name + (extension?.let { " (${it.name})".takeIf { _ -> it.name != source.name } } ?: "")
}

/** Android's `GlobalSearchToolbar` (search field, progress, pinned / all and "has results" chips, bulk selection). */
@Composable
internal fun GlobalSearchToolbar(
    state: SearchScreenModel.State,
    hasPinnedSources: Boolean,
    onBack: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: () -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    bulkSelectionButton: @Composable () -> Unit,
    /** Android's `hideSourceFilter`: no pinned / all chips (the migration search searches its own sources). */
    hideSourceFilter: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(MR.strings.action_bar_up_description))
                }
                OutlinedTextField(
                    value = state.searchQuery.orEmpty(),
                    onValueChange = onChangeSearchQuery,
                    placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("global-search-query"),
                )
                bulkSelectionButton()
            }
            if (state.progress in 1..<state.total) {
                LinearProgressIndicator(
                    progress = { state.progress / state.total.toFloat() },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().testTag("global-search-progress"),
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hideSourceFilter) {
                if (hasPinnedSources) {
                    FilterChip(
                        selected = state.sourceFilter == SourceFilter.PinnedOnly,
                        onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        label = { Text(stringResource(MR.strings.pinned_sources)) },
                        modifier = Modifier.testTag("global-search-pinned"),
                    )
                }
                FilterChip(
                    selected = state.sourceFilter == SourceFilter.All,
                    onClick = { onChangeSearchFilter(SourceFilter.All) },
                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    label = { Text(stringResource(MR.strings.all)) },
                    modifier = Modifier.testTag("global-search-all"),
                )
                VerticalDivider(modifier = Modifier.height(24.dp))
            }
            FilterChip(
                selected = state.onlyShowHasResults,
                onClick = onToggleResults,
                leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                label = { Text(stringResource(MR.strings.has_results)) },
            )
        }
        HorizontalDivider()
    }
}

/** Android's `GlobalSearchContent`: one borrowed `GlobalSearchResultItem` per source. */
@Composable
internal fun GlobalSearchContent(
    state: SearchScreenModel.State,
    getManga: @Composable (Manga) -> androidx.compose.runtime.State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    selection: List<Manga>,
    rowTitle: (Source) -> String,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("global-search-results")) {
        state.filteredItems.forEach { (source, result) ->
            item(key = "global-search-${source.id}") {
                GlobalSearchResultItem(
                    title = rowTitle(source),
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.testTag("global-search-source-${source.id}"),
                ) {
                    if (source is MangayomiJsSource) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) { JsSourceLabel() }
                    }
                    when (result) {
                        SearchItemResult.Loading -> GlobalSearchLoadingResultItem()
                        is SearchItemResult.Success ->
                            GlobalSearchCardRow(result.result, getManga, onClickItem, selection = selection, onLongClick = onLongClickItem)
                        is SearchItemResult.Error -> GlobalSearchErrorResultItem(message = result.throwable.message)
                    }
                }
            }
        }
    }
}

/** Android's `GlobalSearchCardRow` with the desktop cover item (library entries dimmed and marked, like Android). */
@Composable
internal fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> androidx.compose.runtime.State<Manga>,
    onClick: (Manga) -> Unit,
    selection: List<Manga> = emptyList(),
    onLongClick: ((Manga) -> Unit)? = null,
) {
    if (titles.isEmpty()) {
        Text(
            text = stringResource(MR.strings.no_results_found),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    LazyRow(
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(titles, key = { it.id }) {
            val title by getManga(it)
            Column(modifier = Modifier.width(120.dp)) {
                Box(modifier = Modifier.alpha(if (title.favorite) FAVORITE_COVER_ALPHA else 1f)) {
                    CoverItem(
                        manga = title,
                        onClick = { onClick(title) },
                        selected = selection.any { it.id == title.id },
                        onLongClick = onLongClick?.let { { it(title) } },
                    )
                }
                if (title.favorite) {
                    Text(
                        text = stringResource(MR.strings.in_library),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** Android's `CommonMangaItemDefaults.BrowseFavoriteCoverAlpha`. */
private const val FAVORITE_COVER_ALPHA = 0.34f
