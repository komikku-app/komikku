package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourceFeedScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.AllowDuplicateDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.util.nullIfBlank
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.SavedSearch

class SourceFeedScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SourceFeedScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        // KMK <--

        SourceFeedScreen(
            name = screenModel.source.name,
            isLoading = state.isLoading,
            items = state.items,
            hasFilters = state.filters.isNotEmpty(),
            onFabClick = screenModel::openFilterSheet,
            onClickBrowse = { onBrowseClick(navigator, screenModel.source) },
            onClickLatest = { onLatestClick(navigator, screenModel.source) },
            onClickSavedSearch = { onSavedSearchClick(navigator, screenModel.source, it) },
            onClickDelete = screenModel::openDeleteFeed,
            onClickManga = {
                // KMK -->
                if (bulkFavoriteState.selectionMode) {
                    bulkFavoriteScreenModel.toggleSelection(it)
                } else {
                    // KMK <--
                    onMangaClick(navigator, it)
                }
            },
            onClickSearch = { onSearchClick(navigator, screenModel.source, it) },
            searchQuery = state.searchQuery,
            onSearchQueryChange = screenModel::search,
            getMangaState = { screenModel.getManga(initialManga = it) },
            // KMK -->
            sourceId = screenModel.source.id,
            onLongClickManga = { manga ->
                if (!bulkFavoriteState.selectionMode) {
                    scope.launchIO {
                        val duplicateManga = bulkFavoriteScreenModel.getDuplicateLibraryManga(manga)
                        when {
                            manga.favorite -> bulkFavoriteScreenModel.setDialog(
                                BulkFavoriteScreenModel.Dialog.RemoveManga(manga)
                            )
                            duplicateManga != null -> bulkFavoriteScreenModel.setDialog(
                                BulkFavoriteScreenModel.Dialog.AddDuplicateManga(
                                    manga,
                                    duplicateManga,
                                ),
                            )
                            else -> bulkFavoriteScreenModel.addFavorite(manga)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } else {
                    navigator.push(MangaScreen(manga.id, true))
                }
            },
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            // KMK <--
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
            SourceFeedScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = {},
                    onFilter = {
                        screenModel.onFilter { query, filters ->
                            onBrowseClick(
                                navigator = navigator,
                                sourceId = sourceId,
                                search = query,
                                filters = filters,
                            )
                        }
                    },
                    onUpdate = screenModel::setFilters,
                    startExpanded = screenModel.startExpanded,
                    onSave = {},
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        screenModel.onSavedSearch(
                            search,
                            onBrowseClick = { query, searchId ->
                                onBrowseClick(
                                    navigator = navigator,
                                    sourceId = sourceId,
                                    search = query,
                                    savedSearch = searchId,
                                )
                            },
                            onToast = {
                                context.toast(it)
                            },
                        )
                    },
                    onSavedSearchPress = { search ->
                        screenModel.onSavedSearchAddToFeed(search) {
                            context.toast(it)
                        }
                    },
                    openMangaDexRandom = if (screenModel.sourceIsMangaDex) {
                        {
                            screenModel.onMangaDexRandom {
                                navigator.replace(
                                    BrowseSourceScreen(
                                        sourceId,
                                        "id:$it",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    openMangaDexFollows = if (screenModel.sourceIsMangaDex) {
                        {
                            navigator.replace(MangaDexFollowsScreen(sourceId))
                        }
                    } else {
                        null
                    },
                )
            }
            null -> Unit
        }

        // KMK -->
        val onBulkDismissRequest = { bulkFavoriteScreenModel.setDialog(null) }
        when (val dialog = bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onBulkDismissRequest,
                    onConfirm = { bulkFavoriteScreenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                )
            }
            is BulkFavoriteScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onBulkDismissRequest,
                    onConfirm = {
                        bulkFavoriteScreenModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onBulkDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        bulkFavoriteScreenModel.changeMangaFavorite(dialog.manga)
                        bulkFavoriteScreenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onBulkDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, exclude ->
                        bulkFavoriteScreenModel.setMangasCategories(dialog.mangas, include, exclude)
                    },
                )
            }
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate -> {
                AllowDuplicateDialog(
                    onDismissRequest = onBulkDismissRequest,
                    onAllowAllDuplicate = bulkFavoriteScreenModel::addFavoriteDuplicate,
                    onSkipAllDuplicate = {
                        bulkFavoriteScreenModel.addFavoriteDuplicate(skipAllDuplicates = true)
                    },
                    onOpenManga = {
                        navigator.push(MangaScreen(dialog.duplicatedManga.second.id))
                    },
                    onAllowDuplicate = {
                        bulkFavoriteScreenModel.addFavorite(startIdx = dialog.duplicatedManga.first + 1)
                    },
                    onSkipDuplicate = {
                        bulkFavoriteScreenModel.removeDuplicateSelectedManga(index = dialog.duplicatedManga.first)
                        bulkFavoriteScreenModel.addFavorite(startIdx = dialog.duplicatedManga.first)
                    },
                    duplicatedName = dialog.duplicatedManga.second.title,
                )
            }
            else -> {}
        }
        // KMK <--

        BackHandler(state.searchQuery != null || bulkFavoriteState.selectionMode) {
            // KMK -->
            if (bulkFavoriteState.selectionMode) {
                bulkFavoriteScreenModel.backHandler()
            } else {
                // KMK <--
                screenModel.search(null)
            }
        }
    }

    private fun onMangaClick(navigator: Navigator, manga: Manga) {
        navigator.push(MangaScreen(manga.id, true))
    }

    fun onBrowseClick(navigator: Navigator, sourceId: Long, search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        navigator.replace(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
    }

    private fun onLatestClick(navigator: Navigator, source: Source) {
        navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
    }

    fun onBrowseClick(navigator: Navigator, source: Source) {
        navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
    }

    private fun onSavedSearchClick(navigator: Navigator, source: Source, savedSearch: SavedSearch) {
        navigator.replace(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
    }

    private fun onSearchClick(navigator: Navigator, source: Source, query: String) {
        onBrowseClick(navigator, source.id, query.nullIfBlank())
    }
}
