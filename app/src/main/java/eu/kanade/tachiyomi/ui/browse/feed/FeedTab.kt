package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedDeleteConfirmDialog
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.feedTab(
    // KMK -->
    screenModel: FeedScreenModel
    // KMK <--
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    /* KMK -->
    val screenModel = rememberScreenModel { FeedScreenModel() }
    KMK <-- */
    val state by screenModel.state.collectAsState()
    // KMK -->
    val haptic = LocalHapticFeedback.current
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
        actions =
        // KMK -->
        if (state.selection.isNotEmpty())
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.Bookmark,
                    onClick = { },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Filled.Bookmark,
                    onClick = { },
                ),
            )
        else
            // KMK <--
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_add),
                    icon = Icons.Outlined.Add,
                    onClick = {
                        screenModel.openAddDialog()
                    },
                ),
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
                    if (state.selection.isNotEmpty())
                        screenModel.toggleSelection(manga)
                    else
                        // KMK <--
                        navigator.push(MangaScreen(manga.id, true))
                },
                // KMK -->
                onLongClickManga = { manga ->
                    if (state.selection.isNotEmpty()) {
                        navigator.push(MangaScreen(manga.id, true))
                    } else {
                        screenModel.toggleSelection(manga)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
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
