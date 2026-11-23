package app.komikku.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.shared.jssource.JsSourceNotesBanner
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        BrowseScaffold(
            title = source?.name.orEmpty(),
            onBack = navigator::pop,
            actions = {
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
                    CatalogueContent(
                        state = state,
                        supportsLatest = source.supportsLatest,
                        onMode = model::setMode,
                        onLoadMore = model::loadMore,
                        onClick = { navigator.push(MangaDetailsScreen(it.id)) },
                    )
                }
            }
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
    )

    val source: Source? = sourceManager.get(sourceId)

    private var job: Job? = null

    init {
        if (source is CatalogueSource) {
            loadMore()
            // Same as Android's BrowseSourceScreenModel: a source opened outside incognito mode becomes the last used
            // one. A source that is not installed (nothing to browse) is not recorded.
            getIncognitoState.subscribe(sourceId)
                .onEach { incognito -> if (!incognito) sourcePreferences.lastUsedSource().set(sourceId) }
                .launchIn(screenModelScope)
        }
    }

    fun setMode(mode: Mode) {
        job?.cancel()
        mutableState.value = State(mode = mode)
        loadMore()
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
                        is Mode.Search -> source.getSearchManga(page, mode.query, source.getFilterList())
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

@Composable
private fun CatalogueContent(
    state: CatalogueScreenModel.State,
    supportsLatest: Boolean,
    onMode: (CatalogueScreenModel.Mode) -> Unit,
    onLoadMore: () -> Unit,
    onClick: (Manga) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf((state.mode as? CatalogueScreenModel.Mode.Search)?.query.orEmpty()) }
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
                EmptyScreen(message = stringResource(KMR.strings.desktop_source_error, state.error))
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
        AsyncImage(
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
