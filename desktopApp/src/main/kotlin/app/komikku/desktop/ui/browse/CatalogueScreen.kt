package app.komikku.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.webview.WebViewDialogs
import app.komikku.desktop.webview.rememberWebViewLauncher
import app.komikku.shared.jssource.JsSourceNotesBanner
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.SavedSearchCreateDialog
import eu.kanade.presentation.browse.components.SavedSearchDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * Popular / latest / search listing of one source, as a cover grid. With [initialQuery] (global search: "open this
 * source's own search") it starts with that search, like Android's `BrowseSourceScreen(sourceId, query)`.
 */
class CatalogueScreen(private val sourceId: Long, private val initialQuery: String? = null) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CatalogueScreenModel(sourceId, initialQuery) }
        val state by model.state.collectAsState()
        val source = model.source
        // P7: Android's WebView action (`BrowseSourceScreen.onWebViewClick`)
        val webView = rememberWebViewLauncher()
        val onWebViewClick = (source as? HttpSource)?.let { http -> { webView.open(http.getHomeUrl(), http.id) } }
        val context = LocalContext.current
        var savingSearch by remember { mutableStateOf(false) }
        var deletingSearch by remember { mutableStateOf<EXHSavedSearch?>(null) }
        BrowseScaffold(
            title = source?.name.orEmpty(),
            onBack = navigator::pop,
            actions = {
                if (onWebViewClick != null) {
                    IconButton(onClick = onWebViewClick, modifier = Modifier.testTag("catalogue-webview")) {
                        Icon(Icons.Outlined.Public, contentDescription = stringResource(MR.strings.action_open_in_web_view))
                    }
                }
                if (source is ConfigurableSource) {
                    IconButton(onClick = { navigator.push(SourcePreferencesScreen(sourceId)) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                    }
                }
            },
        ) {
            if (source !is CatalogueSource) {
                EmptyScreen(stringRes = KMR.strings.desktop_source_not_installed)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    (source as? MangayomiJsSource)?.descriptor?.notes?.takeIf { it.isNotBlank() }?.let { JsSourceNotesBanner(it) }
                    SavedSearchesRow(
                        savedSearches = state.savedSearches,
                        onApply = { model.applySavedSearch(it) { context.toast(SYMR.strings.save_search_invalid) } },
                        onSave = { savingSearch = true },
                        onDelete = { deletingSearch = it },
                    )
                    CatalogueContent(
                        state = state,
                        supportsLatest = source.supportsLatest,
                        onMode = model::setMode,
                        onLoadMore = model::loadMore,
                        onClick = { navigator.push(MangaDetailsScreen(it.id)) },
                        onWebViewClick = onWebViewClick,
                    )
                }
            }
        }
        WebViewDialogs(webView)
        // P13: Android's saved search dialogs (BrowseSourceDialogs), a duplicate or blank name is rejected there
        if (savingSearch) {
            SavedSearchCreateDialog(
                onDismissRequest = { savingSearch = false },
                currentSavedSearches = state.savedSearches.map { it.name }.toImmutableList(),
                saveSearch = model::saveSearch,
            )
        }
        deletingSearch?.let { search ->
            SavedSearchDeleteDialog(
                onDismissRequest = { deletingSearch = null },
                name = search.name,
                deleteSavedSearch = { model.deleteSearch(search.id) },
            )
        }
    }
}

class CatalogueScreenModel(
    sourceId: Long,
    initialQuery: String? = null,
    sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getIncognitoState: GetIncognitoState = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
) : StateScreenModel<CatalogueScreenModel.State>(
    State(mode = initialQuery?.takeIf { it.isNotBlank() }?.let { Mode.Search(it) } ?: Mode.Popular),
) {

    sealed interface Mode {
        data object Popular : Mode
        data object Latest : Mode
        data class Search(val query: String) : Mode
    }

    data class State(
        val mode: Mode = Mode.Popular,
        val manga: List<Manga> = emptyList(),
        val page: Int = 0,
        val hasNextPage: Boolean = true,
        val isLoading: Boolean = false,
        val error: String? = null,
        /** The filters a search runs with (P13: restored by a saved search; the source's defaults until then). */
        val filters: FilterList = FilterList(),
        /** The source's saved searches, sorted by name like Android's filter sheet. */
        val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
    )

    val source: Source? = sourceManager.get(sourceId)

    private var job: Job? = null

    init {
        if (source is CatalogueSource) {
            mutableState.update { it.copy(filters = source.getFilterList()) }
            loadMore()
            getExhSavedSearch.subscribe(sourceId, source::getFilterList)
                .map { it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name)) }
                .onEach { searches -> mutableState.update { it.copy(savedSearches = searches.toImmutableList()) } }
                .launchIn(screenModelScope)
            // Same as Android's BrowseSourceScreenModel: a source opened outside incognito mode becomes the last used
            // one. A source that is not installed (nothing to browse) is not recorded.
            getIncognitoState.subscribe(sourceId)
                .onEach { incognito -> if (!incognito) sourcePreferences.lastUsedSource().set(sourceId) }
                .launchIn(screenModelScope)
        }
    }

    fun setMode(mode: Mode) {
        job?.cancel()
        mutableState.update { State(mode = mode, filters = it.filters, savedSearches = it.savedSearches) }
        loadMore()
    }

    /** Sets the filters the next search runs with (the desktop has no filter sheet yet; tests and saved searches). */
    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    /**
     * Same as Android's `BrowseSourceScreenModel.onSavedSearch`: reloads the saved search, refuses one whose filters no
     * longer match the source's, otherwise searches its query with its filters (the source's defaults when it has none).
     */
    fun applySavedSearch(loaded: EXHSavedSearch, onInvalid: () -> Unit) {
        val source = source as? CatalogueSource ?: return
        screenModelScope.launch {
            val search = getExhSavedSearch.awaitOne(loaded.id, source::getFilterList) ?: loaded
            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                onInvalid()
                return@launch
            }
            mutableState.update { it.copy(filters = search.filterList ?: source.getFilterList()) }
            setMode(Mode.Search(search.query.orEmpty()))
        }
    }

    /** Same as Android's `BrowseSourceScreenModel.saveSearch`: the current search query (if any) and filters. */
    fun saveSearch(name: String) {
        val source = source as? CatalogueSource ?: return
        val current = state.value
        screenModelScope.launch {
            withContext(NonCancellable) {
                val query = (current.mode as? Mode.Search)?.query?.takeUnless { it.isBlank() }?.trim()
                val filterList = current.filters.ifEmpty { source.getFilterList() }
                withIOContext {
                    insertSavedSearch.await(
                        SavedSearch(
                            id = -1,
                            source = source.id,
                            name = name.trim(),
                            query = query,
                            filtersJson = runCatching {
                                filterSerializer.serialize(filterList).ifEmpty { null }?.let { Json.encodeToString(it) }
                            }.getOrNull(),
                        ),
                    )
                }
            }
        }
    }

    fun deleteSearch(savedSearchId: Long) {
        screenModelScope.launch {
            withContext(NonCancellable) { withIOContext { deleteSavedSearchById.await(savedSearchId) } }
        }
    }

    fun loadMore() {
        val source = source as? CatalogueSource ?: return
        val current = state.value
        if (current.isLoading || !current.hasNextPage) return
        mutableState.update { it.copy(isLoading = true, error = null) }
        job = screenModelScope.launch {
            try {
                val page = current.page + 1
                val result = withIOContext {
                    when (val mode = current.mode) {
                        Mode.Popular -> source.getPopularManga(page)
                        Mode.Latest -> source.getLatestUpdates(page)
                        is Mode.Search -> source.getSearchManga(page, mode.query, current.filters)
                    }
                }
                // Same as the Android browse paging source: store the entries so they have ids
                val manga = withIOContext { networkToLocalManga(result.mangas.map { it.toDomainManga(source.id) }) }
                mutableState.update {
                    it.copy(
                        manga = (it.manga + manga).distinctBy { entry -> entry.id },
                        page = page,
                        hasNextPage = result.hasNextPage,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                mutableState.update { it.copy(isLoading = false, error = e.message ?: e::class.simpleName) }
            }
        }
    }
}

/** P13: the source's saved searches (Android lists them in the filter sheet): apply, save the current one, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedSearchesRow(
    savedSearches: ImmutableList<EXHSavedSearch>,
    onApply: (EXHSavedSearch) -> Unit,
    onSave: () -> Unit,
    onDelete: (EXHSavedSearch) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(SYMR.strings.saved_searches), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onSave, modifier = Modifier.testTag("catalogue-save-search")) {
            Icon(Icons.Outlined.BookmarkAdd, contentDescription = stringResource(SYMR.strings.save_search))
        }
        savedSearches.forEach { search ->
            InputChip(
                selected = false,
                onClick = { onApply(search) },
                label = { Text(search.name) },
                trailingIcon = {
                    IconButton(onClick = { onDelete(search) }, modifier = Modifier.testTag("saved-search-delete-${search.name}")) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(SYMR.strings.save_search_delete))
                    }
                },
            )
        }
    }
}

@Composable
private fun CatalogueContent(
    state: CatalogueScreenModel.State,
    supportsLatest: Boolean,
    onMode: (CatalogueScreenModel.Mode) -> Unit,
    onLoadMore: () -> Unit,
    onClick: (Manga) -> Unit,
    onWebViewClick: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf((state.mode as? CatalogueScreenModel.Mode.Search)?.query.orEmpty()) }
    // P13: an applied saved search shows its query in the search field
    LaunchedEffect(state.mode) { (state.mode as? CatalogueScreenModel.Mode.Search)?.let { query = it.query } }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.mode == CatalogueScreenModel.Mode.Popular,
                onClick = { onMode(CatalogueScreenModel.Mode.Popular) },
                label = { Text(stringResource(MR.strings.popular)) },
            )
            if (supportsLatest) {
                FilterChip(
                    selected = state.mode == CatalogueScreenModel.Mode.Latest,
                    onClick = { onMode(CatalogueScreenModel.Mode.Latest) },
                    label = { Text(stringResource(MR.strings.latest)) },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onMode(CatalogueScreenModel.Mode.Search(query)) }),
                trailingIcon = {
                    IconButton(onClick = { onMode(CatalogueScreenModel.Mode.Search(query)) }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        when {
            state.manga.isEmpty() && state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.manga.isEmpty() && state.error != null ->
                EmptyScreen(
                    message = stringResource(KMR.strings.desktop_source_error, state.error),
                    // Android's browse error actions (retry, WebView), e.g. to pass a Cloudflare check (P7)
                    actions = listOfNotNull(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, onLoadMore),
                        onWebViewClick?.let { EmptyScreenAction(MR.strings.action_open_in_web_view, Icons.Outlined.Public, it) },
                    ).toImmutableList(),
                )
            state.manga.isEmpty() && !state.hasNextPage -> EmptyScreen(stringRes = MR.strings.no_results_found)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.manga, key = { it.id }) { manga ->
                    CoverItem(manga = manga, onClick = { onClick(manga) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            state.isLoading -> CircularProgressIndicator()
                            state.error != null -> Text(stringResource(KMR.strings.desktop_source_error, state.error))
                            state.hasNextPage -> Button(onClick = onLoadMore) { Text(stringResource(KMR.strings.desktop_load_more)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverItem(manga: Manga, onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        CoverImage(
            model = manga.asMangaCover(),
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}
