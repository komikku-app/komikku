package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedDeleteConfirmDialog
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun Screen.feedTab(): TabContent {
    val router = LocalRouter.currentOrThrow
    val screenModel = rememberScreenModel { FeedScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = R.string.feed,
        actions = listOf(
            AppBar.Action(
                title = stringResource(R.string.action_add),
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
                    router.pushController(
                        BrowseSourceController(
                            source,
                            savedSearch = savedSearch.id,
                        ),
                    )
                },
                onClickSource = { source ->
                    screenModel.sourcePreferences.lastUsedSource().set(source.id)
                    router.pushController(
                        BrowseSourceController(
                            source,
                            GetRemoteManga.QUERY_LATEST,
                        ),
                    )
                },
                onClickDelete = screenModel::openDeleteDialog,
                onClickManga = { manga ->
                    router.pushController(MangaController(manga.id, true))
                },
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

            val internalErrString = stringResource(R.string.internal_error)
            val tooManyFeedsString = stringResource(R.string.too_many_in_feed)
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
