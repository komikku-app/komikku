package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.FeedOrderListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedState
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun SourceFeedOrderScreen(
    state: SourceFeedState,
    onClickDelete: (FeedSavedSearch) -> Unit,
    changeOrder: (FeedSavedSearch, Int) -> Unit,
    onClickSortAlphabetically: () -> Unit,
    navigateUp: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = {
                    AppBarTitle(stringResource(KMR.strings.action_sort_feed))
                },
                navigateUp = navigateUp,
                actions = {
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_sort),
                            icon = Icons.Outlined.SortByAlpha,
                            onClick = onClickSortAlphabetically,
                        ),
                    )
                },
                isActionMode = false,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen()
            state.items
                .filterIsInstance<SourceFeedUI.SourceSavedSearch>()
                .isEmpty() -> EmptyScreen(
                stringRes = MR.strings.empty_screen,
            )

            else -> {
                val lazyListState = rememberLazyListState()
                val feeds = state.items
                    .filterIsInstance<SourceFeedUI.SourceSavedSearch>()

                var reorderableList by remember { mutableStateOf(feeds) }
                val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    reorderableList = reorderableList.toMutableList().apply {
                        changeOrder(reorderableList[from.index].feed, to.index - from.index)
                        add(to.index, removeAt(from.index))
                    }
                }

                LaunchedEffect(feeds) {
                    if (!reorderableLazyColumnState.isAnyItemDragging) {
                        reorderableList = feeds
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = paddingValues + topSmallPaddingValues +
                        PaddingValues(horizontal = MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(
                        items = reorderableList,
                        key = { it.feed.key },
                    ) { feed ->
                        ReorderableItem(reorderableLazyColumnState, feed.feed.key) {
                            FeedOrderListItem(
                                modifier = Modifier.animateItem(),
                                title = feed.title,
                                onDelete = { onClickDelete(feed.feed) },
                            )
                        }
                    }
                }
            }
        }
    }
}
