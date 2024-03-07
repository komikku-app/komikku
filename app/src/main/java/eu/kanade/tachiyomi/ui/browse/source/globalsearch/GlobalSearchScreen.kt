package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.AllowDuplicateDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
        }

        // KMK -->
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = state.selectionMode) {
            when {
                state.selectionMode -> screenModel.toggleSelectionMode()
            }
        }
        // KMK <--

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    SearchItemResult.Loading -> return@LaunchedEffect
                    is SearchItemResult.Success -> {
                        val manga = result.result.singleOrNull()
                        if (manga != null) {
                            navigator.replace(MangaScreen(manga.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            GlobalSearchScreen(
                // KMK -->
                screenModel = screenModel,
                // KMK <--
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getManga = { screenModel.getManga(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = {
                    // KMK -->
                    if (state.selectionMode)
                        screenModel.toggleSelection(it)
                    else
                    // KMK <--
                        navigator.push(MangaScreen(it.id, true))
                },
                onLongClickItem = { manga ->
                    // KMK -->
                    if (state.selectionMode)
                    // KMK <--
                        navigator.push(MangaScreen(manga.id, true))
                    // KMK -->
                    else
                        scope.launchIO {
                            val duplicateManga = screenModel.getDuplicateLibraryManga(manga)
                            when {
                                manga.favorite -> screenModel.setDialog(SearchScreenModel.Dialog.RemoveManga(manga))
                                duplicateManga != null -> screenModel.setDialog(
                                    SearchScreenModel.Dialog.AddDuplicateManga(
                                        manga,
                                        duplicateManga,
                                    ),
                                )
                                else -> screenModel.addFavorite(manga)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    // KMK <--
                },
            )
        }

        // KMK -->
        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                )
            }
            is SearchScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is SearchScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            is SearchScreenModel.Dialog.ChangeMangasCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, exclude ->
                        screenModel.setMangaCategories(dialog.mangas, include, exclude)
                    },
                )
            }
            is SearchScreenModel.Dialog.AllowDuplicate -> {
                AllowDuplicateDialog(
                    onDismissRequest = onDismissRequest,
                    onAllowAllDuplicate = {
                        screenModel.addFavoriteDuplicate()
                    },
                    onSkipAllDuplicate = {
                        screenModel.addFavoriteDuplicate(skipAllDuplicates = true)
                    },
                    onOpenManga = {
                        navigator.push(MangaScreen(dialog.duplicatedManga.second.id))
                    },
                    onAllowDuplicate = {
                        screenModel.addFavorite(startIdx = dialog.duplicatedManga.first + 1)
                    },
                    onSkipDuplicate = {
                        screenModel.removeDuplicateSelectedManga(index = dialog.duplicatedManga.first)
                        screenModel.addFavorite(startIdx = dialog.duplicatedManga.first)
                    },
                    duplicatedName = dialog.duplicatedManga.second.title,
                )
            }
            else -> {}
        }
        // KMK <--
    }
}
