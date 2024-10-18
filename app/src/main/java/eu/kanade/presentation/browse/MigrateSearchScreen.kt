package eu.kanade.presentation.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.presentation.components.SelectionToolbar
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun MigrateSearchScreen(
    state: SearchScreenModel.State,
    fromSourceId: Long?,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    // KMK -->
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    hasPinnedSources: Boolean,
    // KMK <--
) {
    // KMK -->
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            if (bulkFavoriteState.selectionMode) {
                SelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClicked = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        state.filteredItems.forEach { (_, result) ->
                            when (result) {
                                is SearchItemResult.Success -> {
                                    result.result.forEach { manga ->
                                        if (!bulkFavoriteState.selection.contains(manga)) {
                                            bulkFavoriteScreenModel.select(manga)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    },
                )
            } else {
                // KMK <--
                GlobalSearchToolbar(
                    searchQuery = state.searchQuery,
                    progress = state.progress,
                    total = state.total,
                    navigateUp = navigateUp,
                    onChangeSearchQuery = onChangeSearchQuery,
                    onSearch = onSearch,
                    sourceFilter = state.sourceFilter,
                    onChangeSearchFilter = onChangeSearchFilter,
                    onlyShowHasResults = state.onlyShowHasResults,
                    onToggleResults = onToggleResults,
                    scrollBehavior = scrollBehavior,
                    // KMK -->
                    toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    hasPinnedSources = hasPinnedSources,
                    // KMK <--
                )
            }
        },
    ) { paddingValues ->
        GlobalSearchContent(
            fromSourceId = fromSourceId,
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            // KMK -->
            selection = bulkFavoriteState.selection,
            // KMK <--
        )
    }
}
