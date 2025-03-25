package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.FeedOrderListItem
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun FeedOrderScreen(
    state: FeedScreenState,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onChangeOrder: (FeedSavedSearch, Int) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen()
        state.isEmpty || state.items.isNullOrEmpty() -> EmptyScreen(
            stringRes = MR.strings.empty_screen,
        )

        else -> {
            val lazyListState = rememberLazyListState()
            val feeds = state.items.toImmutableList()

            val feedsState = remember { feeds.toMutableStateList() }
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val item = feedsState.removeAt(from.index)
                feedsState.add(to.index, item)
                onChangeOrder(item.feed, to.index)
            }

            LaunchedEffect(feeds) {
                if (!reorderableState.isAnyItemDragging) {
                    feedsState.clear()
                    feedsState.addAll(feeds)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = topSmallPaddingValues +
                    PaddingValues(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(
                    items = feedsState,
                    key = { it.feed.key },
                ) { feed ->
                    ReorderableItem(reorderableState, feed.feed.key) {
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

internal val FeedSavedSearch.key inline get() = "feed-$id"
