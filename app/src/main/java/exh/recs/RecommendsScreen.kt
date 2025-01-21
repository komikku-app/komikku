package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.AddDuplicateMangaDialog
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeMangaCategoryDialog
import eu.kanade.tachiyomi.ui.browse.ChangeMangasCategoryDialog
import eu.kanade.tachiyomi.ui.browse.RemoveMangaDialog
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.components.RecommendsScreen
import exh.ui.ifSourcesLoaded
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

class RecommendsScreen(val mangaId: Long, val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            RecommendsScreenModel(mangaId = mangaId, sourceId = sourceId)
        }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val scope = rememberCoroutineScope()
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.toggleSelectionMode()
        }
        // KMK <--

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (manga.source) {
                    -1L -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    else -> MangaScreen(manga.id, true)
                },
            )
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                -1L -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> onClickItem(manga)
            }
        }

        RecommendsScreen(
            manga = screenModel.manga,
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(
                    BrowseRecommendsScreen(
                        mangaId,
                        sourceId,
                        pagingSource::class.qualifiedName!!,
                        pagingSource.associatedSourceId == null,
                    ),
                )
            },
            onClickItem = {
                // KMK -->
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        onClickItem(manga)
                    }
                }
            },
            onLongClickItem = {
                // KMK -->
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        // KMK <--
                        onLongClickItem(manga)
                    }
                }
            },
            // KMK -->
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            displayMode = screenModel.displayMode,
            onDisplayModeChange = { screenModel.displayMode = it },
            // KMK <--
        )

        // KMK -->
        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga ->
                AddDuplicateMangaDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.RemoveManga ->
                RemoveMangaDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory ->
                ChangeMangaCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeMangasCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)
            else -> {}
        }
        // KMK <--
    }
}
