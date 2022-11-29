package eu.kanade.tachiyomi.ui.browse.source.feed

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.presentation.browse.SourceFeedScreen
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.browse.components.SourceFeedFailedToLoadSavedSearchDialog
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterSheet
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import exh.savedsearches.models.SavedSearch
import exh.util.nullIfBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class SourceFeedScreen(val sourceId: Long) : Screen {

    @Transient
    private var filterSheet: SourceFilterSheet? = null

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SourceFeedScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val router = LocalRouter.currentOrThrow

        SourceFeedScreen(
            name = screenModel.source.name,
            isLoading = state.isLoading,
            items = state.items,
            onFabClick = if (state.filters.isEmpty()) null else { { filterSheet?.show() } },
            onClickBrowse = { onBrowseClick(router, screenModel.source) },
            onClickLatest = { onLatestClick(router, screenModel.source) },
            onClickSavedSearch = { onSavedSearchClick(router, screenModel.source, it) },
            onClickDelete = screenModel::openDeleteFeed,
            onClickManga = { onMangaClick(navigator, it) },
            onClickSearch = { onSearchClick(router, screenModel.source, it) },
            searchQuery = state.searchQuery,
            onSearchQueryChange = screenModel::search,
            isIncognitoMode = screenModel.isIncognitoMode,
            isDownloadOnly = screenModel.isDownloadOnly,
            getMangaState = { screenModel.getManga(initialManga = it) },
        )

        val onDismissRequest = screenModel::dismissDialog
        when (val dialog = state.dialog) {
            is SourceFeedScreenModel.Dialog.AddFeed -> {
                SourceFeedAddDialog(
                    onDismissRequest = onDismissRequest,
                    name = dialog.name,
                    addFeed = {
                        screenModel.createFeed(dialog.feedId)
                        onDismissRequest()
                    },
                )
            }
            is SourceFeedScreenModel.Dialog.DeleteFeed -> {
                SourceFeedDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    deleteFeed = {
                        screenModel.deleteFeed(dialog.feed)
                        onDismissRequest()
                    },
                )
            }
            SourceFeedScreenModel.Dialog.FailedToLoadSavedSearch -> {
                SourceFeedFailedToLoadSavedSearchDialog(onDismissRequest)
            }
            null -> Unit
        }

        BackHandler(state.searchQuery != null) {
            screenModel.search(null)
        }

        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        LaunchedEffect(state.filters) {
            initFilterSheet(state, screenModel, scope, context, router)
        }
    }

    fun initFilterSheet(
        state: SourceFeedState,
        screenModel: SourceFeedScreenModel,
        viewScope: CoroutineScope,
        context: Context,
        router: Router,
    ) {
        val filterSerializer = FilterSerializer()
        filterSheet = SourceFilterSheet(
            context,
            // SY -->
            router,
            screenModel.source,
            emptyList(),
            // SY <--
            onFilterClicked = {
                val allDefault = state.filters == screenModel.source.getFilterList()
                filterSheet?.dismiss()
                if (allDefault) {
                    onBrowseClick(
                        router,
                        screenModel.source.id,
                        state.searchQuery?.nullIfBlank(),
                    )
                } else {
                    onBrowseClick(
                        router,
                        screenModel.source.id,
                        state.searchQuery?.nullIfBlank(),
                        filters = Json.encodeToString(filterSerializer.serialize(state.filters)),
                    )
                }
            },
            onResetClicked = {},
            onSaveClicked = {},
            onSavedSearchClicked = { idOfSearch ->
                viewScope.launchUI {
                    val search = screenModel.loadSearch(idOfSearch)

                    if (search == null) {
                        screenModel.openFailedToLoadSavedSearch()
                        return@launchUI
                    }

                    if (search.filterList == null && state.filters.isNotEmpty()) {
                        context.toast(R.string.save_search_invalid)
                        return@launchUI
                    }

                    if (search.filterList != null) {
                        screenModel.setFilters(FilterList(search.filterList))
                        filterSheet?.setFilters(state.filterItems)
                    }
                    val allDefault = search.filterList != null && state.filters == screenModel.source.getFilterList()
                    filterSheet?.dismiss()

                    if (!allDefault) {
                        onBrowseClick(
                            router,
                            screenModel.source.id,
                            search = state.searchQuery?.nullIfBlank(),
                            savedSearch = search.id,
                        )
                    }
                }
            },
            onSavedSearchDeleteClicked = { idOfSearch, name ->
                viewScope.launchUI {
                    if (screenModel.hasTooManyFeeds()) {
                        context.toast(R.string.too_many_in_feed)
                        return@launchUI
                    }
                    screenModel.openAddFeed(idOfSearch, name)
                }
            },
        )
        viewScope.launchUI {
            filterSheet?.setSavedSearches(screenModel.loadSearches())
        }
        filterSheet?.setFilters(state.filterItems)
    }

    private fun onMangaClick(navigator: Navigator, manga: Manga) {
        navigator.push(MangaScreen(manga.id, true))
    }

    fun onBrowseClick(router: Router, sourceId: Long, search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        router.replaceTopController(BrowseSourceController(sourceId, search, savedSearch = savedSearch, filterList = filters).withFadeTransaction())
    }

    private fun onLatestClick(router: Router, source: CatalogueSource) {
        router.replaceTopController(BrowseSourceController(source, GetRemoteManga.QUERY_LATEST).withFadeTransaction())
    }

    fun onBrowseClick(router: Router, source: CatalogueSource) {
        router.replaceTopController(BrowseSourceController(source, GetRemoteManga.QUERY_POPULAR).withFadeTransaction())
    }

    private fun onSavedSearchClick(router: Router, source: CatalogueSource, savedSearch: SavedSearch) {
        router.replaceTopController(BrowseSourceController(source, savedSearch = savedSearch.id).withFadeTransaction())
    }

    private fun onSearchClick(router: Router, source: CatalogueSource, query: String) {
        onBrowseClick(router, source.id, query.nullIfBlank())
    }
}
