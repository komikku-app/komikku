package eu.kanade.tachiyomi.ui.browse.feed

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedDeleteConfirmDialog
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.AllowDuplicateDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.feedTab(
    // KMK -->
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    // KMK <--
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { FeedScreenModel() }
    val state by screenModel.state.collectAsState()

    // KMK -->
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = bulkFavoriteState.selectionMode) {
        bulkFavoriteScreenModel.backHandler()
    }

    LaunchedEffect(bulkFavoriteState.selectionMode) {
        HomeScreen.showBottomNav(!bulkFavoriteState.selectionMode)
    }
    // KMK <--

    DisposableEffect(navigator.lastEvent) {
        if (navigator.lastEvent == StackEvent.Push) {
            screenModel.pushed = true
        } else if (!screenModel.pushed) {
            screenModel.init()
        }

        onDispose {
            if (navigator.lastEvent == StackEvent.Idle && screenModel.pushed) {
                screenModel.pushed = false
            }
        }
    }

    return TabContent(
        titleRes = SYMR.strings.feed,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_add),
                icon = Icons.Outlined.Add,
                onClick = {
                    screenModel.openAddDialog()
                },
            ),
            // KMK -->
            AppBar.Action(
                title = stringResource(MR.strings.action_bulk_select),
                icon = Icons.Outlined.Checklist,
                onClick = bulkFavoriteScreenModel::toggleSelectionMode,
            ),
            // KMK <--
        ),
        content = { contentPadding, snackbarHostState ->
            FeedScreen(
                state = state,
                contentPadding = contentPadding,
                onClickSavedSearch = { savedSearch, source ->
                    screenModel.sourcePreferences.lastUsedSource().set(savedSearch.source)
                    navigator.push(
                        BrowseSourceScreen(
                            source.id,
                            listingQuery = null,
                            savedSearch = savedSearch.id,
                        ),
                    )
                },
                onClickSource = { source ->
                    screenModel.sourcePreferences.lastUsedSource().set(source.id)
                    navigator.push(
                        BrowseSourceScreen(
                            source.id,
                            // KMK -->
                            listingQuery = if (!source.supportsLatest)
                                GetRemoteManga.QUERY_POPULAR
                            else
                                GetRemoteManga.QUERY_LATEST,
                            // KMK <--
                        ),
                    )
                },
                onClickDelete = screenModel::openDeleteDialog,
                onClickManga = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                // KMK -->
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
                selection = bulkFavoriteState.selection,
                // KMK <--
                onRefresh = screenModel::init,
                getMangaState = { manga, source -> screenModel.getManga(initialManga = manga, source = source) },
            )

            state.dialog?.let { dialog ->
                when (dialog) {
                    is FeedScreenModel.Dialog.AddFeed -> {
                        FeedAddDialog(
                            sources = dialog.options,
                            onDismiss = screenModel::dismissDialog,
                            onClickAdd = {
                                if (it != null) {
                                    screenModel.openAddSearchDialog(it)
                                }
                                screenModel.dismissDialog()
                            },
                        )
                    }
                    is FeedScreenModel.Dialog.AddFeedSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.options,
                            onDismiss = screenModel::dismissDialog,
                            onClickAdd = { source, savedSearch ->
                                screenModel.createFeed(source, savedSearch)
                                screenModel.dismissDialog()
                            },
                        )
                    }
                    is FeedScreenModel.Dialog.DeleteFeed -> {
                        FeedDeleteConfirmDialog(
                            feed = dialog.feed,
                            onDismiss = screenModel::dismissDialog,
                            onClickDeleteConfirm = {
                                screenModel.deleteFeed(it)
                                screenModel.dismissDialog()
                            },
                        )
                    }
                }
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

            val internalErrString = stringResource(MR.strings.internal_error)
            val tooManyFeedsString = stringResource(SYMR.strings.too_many_in_feed)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        FeedScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        FeedScreenModel.Event.TooManyFeeds -> {
                            launch { snackbarHostState.showSnackbar(tooManyFeedsString) }
                        }
                    }
                }
            }
        },
    )
}
