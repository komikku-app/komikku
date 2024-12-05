package eu.kanade.tachiyomi.ui.manga

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RelatedMangasContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun RelatedMangasScreen(
    screenModel: MangaScreenModel,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    navigateUp: () -> Unit,
    navigator: Navigator,
    scope: CoroutineScope,
    successState: MangaScreenModel.State.Success,
) {
    val sourcePreferences: SourcePreferences = Injekt.get()
    var displayMode by sourcePreferences.sourceDisplayMode().asState(scope)

    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            if (bulkFavoriteState.selectionMode) {
                BulkSelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    isRunning = bulkFavoriteState.isRunning,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        successState.relatedMangasSorted?.forEach {
                            val relatedManga = it as RelatedManga.Success
                            relatedManga.mangaList.forEach { manga ->
                                bulkFavoriteScreenModel.select(manga)
                            }
                        }
                    },
                    onReverseSelection = {
                        successState.relatedMangasSorted
                            ?.map { it as RelatedManga.Success }
                            ?.flatMap { it.mangaList }
                            ?.let { bulkFavoriteScreenModel.reverseSelection(it) }
                    },
                )
            } else {
                BrowseSourceSimpleToolbar(
                    navigateUp = navigateUp,
                    title = successState.manga.title,
                    displayMode = displayMode,
                    onDisplayModeChange = { displayMode = it },
                    scrollBehavior = scrollBehavior,
                    toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    isRunning = bulkFavoriteState.isRunning,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        RelatedMangasContent(
            relatedMangas = successState.relatedMangasSorted,
            getMangaState = { manga -> screenModel.getManga(initialManga = manga) },
            columns = getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = displayMode,
            contentPadding = paddingValues,
            onMangaClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                }
            },
            onMangaLongClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                }
            },
            onKeywordClick = { query ->
                navigator.push(BrowseSourceScreen(successState.source.id, query))
            },
            onKeywordLongClick = { query ->
                navigator.push(GlobalSearchScreen(query))
            },
            selection = bulkFavoriteState.selection,
        )
    }
}

private fun getColumnsPreference(orientation: Int): GridCells {
    val libraryPreferences: LibraryPreferences = Injekt.get()

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) {
        libraryPreferences.landscapeColumns()
    } else {
        libraryPreferences.portraitColumns()
    }.get()
    return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
}
