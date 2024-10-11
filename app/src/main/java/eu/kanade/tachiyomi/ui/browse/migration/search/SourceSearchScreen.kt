package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceFloatingActionButton
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeMangasCategoryDialog
import eu.kanade.tachiyomi.ui.browse.bulkSelectionButton
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import exh.ui.ifSourcesLoaded
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource

/**
 * Opened when click on a source in [MigrateSearchScreen]
 */
data class SourceSearchScreen(
    private val oldManga: Manga,
    private val sourceId: Long,
    private val query: String?,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { BrowseSourceScreenModel(sourceId, query) }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.toggleSelectionMode()
        }
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
                            state.mangaDisplayingList.forEach { manga ->
                                bulkFavoriteScreenModel.select(manga)
                            }
                        },
                        onReverseSelection = {
                            bulkFavoriteScreenModel.reverseSelection(state.mangaDisplayingList.toList())
                        },
                    )
                } else {
                    // KMK <--
                    SearchToolbar(
                        searchQuery = state.toolbarQuery ?: "",
                        onChangeSearchQuery = screenModel::setToolbarQuery,
                        onClickCloseSearch = navigator::pop,
                        onSearch = screenModel::search,
                        scrollBehavior = scrollBehavior,
                        // KMK -->
                        actions = {
                            AppBarActions(
                                actions = persistentListOf(
                                    bulkSelectionButton(
                                        isRunning = bulkFavoriteState.isRunning,
                                        toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                                    ),
                                ),
                            )
                        },
                        // KMK <--
                    )
                }
            },
            floatingActionButton = {
                // SY -->
                BrowseSourceFloatingActionButton(
                    isVisible = state.filters.isNotEmpty(),
                    onFabClick = screenModel::openFilterSheet,
                )
                // SY <--
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.mangaPagerFlowFlow.collectAsState()
            val openMigrateDialog: (Manga) -> Unit = {
                // SY -->
                navigator.items
                    .filterIsInstance<MigrationListScreen>()
                    .last()
                    .newSelectedItem = oldManga.id to it.id
                navigator.popUntil { it is MigrationListScreen }
                // SY <--
            }
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = screenModel.ehentaiBrowseDisplayMode,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = screenModel.source as? HttpSource ?: return@BrowseSourceContent
                    navigator.push(
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = source.name,
                            sourceId = source.id,
                        ),
                    )
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) },
                onMangaClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        openMigrateDialog(manga)
                    }
                },
                onMangaLongClick = { navigator.push(MangaScreen(it.id, true)) },
                // KMK -->
                selection = bulkFavoriteState.selection,
                browseSourceState = state,
                // KMK <--
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (state.dialog) {
            is BrowseSourceScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    // SY -->
                    startExpanded = screenModel.startExpanded,
                    onSave = {},
                    savedSearches = persistentListOf(),
                    onSavedSearch = {},
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(SYMR.strings.saved_searches),
                    // KMK <--
                    onSavedSearchPress = {},
                    openMangaDexRandom = null,
                    openMangaDexFollows = null,
                    // SY <--
                )
            }
            else -> {}
        }

        // KMK -->
        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeMangasCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)
            else -> {}
        }
        // KMK <--
    }
}
