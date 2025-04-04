package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

class BrowseRecommendsScreen(
    private val mangaId: Long,
    private val sourceId: Long,
    private val recommendationSourceName: String,
    private val isExternalSource: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            BrowseRecommendsScreenModel(mangaId, sourceId, recommendationSourceName)
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (isExternalSource) {
                    true -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    false -> MangaScreen(manga.id, true)
                },
            )
        }

        // KMK -->
        val onLongClickItem = { manga: Manga ->
            when (isExternalSource) {
                true -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                false -> navigator.push(MangaScreen(manga.id, true))
            }
        }

        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                // KMK -->
                if (bulkFavoriteState.selectionMode) {
                    BulkSelectionToolbar(
                        selectedCount = bulkFavoriteState.selection.size,
                        isRunning = bulkFavoriteState.isRunning,
                        onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                        onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                        onSelectAll = {
                            mangaList.itemSnapshotList.items
                                .map { it.value.first }
                                .forEach { manga ->
                                    bulkFavoriteScreenModel.select(manga)
                                }
                        },
                        onReverseSelection = {
                            bulkFavoriteScreenModel.reverseSelection(
                                mangaList.itemSnapshotList.items
                                    .map { it.value.first },
                            )
                        },
                    )
                } else {
                    // KMK <--
                    val title = remember {
                        val recSource = screenModel.recommendationSource
                        "${recSource.name} (${recSource.category.getString(context)})"
                    }

                    BrowseSourceSimpleToolbar(
                        navigateUp = navigator::pop,
                        title = title,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        scrollBehavior = scrollBehavior,
                        // KMK -->
                        toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                        isRunning = bulkFavoriteState.isRunning,
                        // KMK <--
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = mangaList,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = false,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        onClickItem(manga)
                    }
                },
                onMangaLongClick = { manga ->
                    // KMK -->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        onLongClickItem(manga)
                    }
                    // KMK <--
                },
                // KMK -->
                selection = bulkFavoriteState.selection,
                // KMK <--
            )
        }

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
