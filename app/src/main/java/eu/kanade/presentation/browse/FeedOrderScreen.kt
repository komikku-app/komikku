package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.FeedOrderListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.domain.source.model.FeedSavedSearch as Feed

@Composable
fun FeedOrderScreen(
    state: FeedScreenState,
    onClickSortAlphabetically: () -> Unit,
    onClickDelete: (Feed) -> Unit,
    onClickMoveUp: (Feed) -> Unit,
    onClickMoveDown: (Feed) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_sort),
                                icon = Icons.Outlined.SortByAlpha,
                                onClick = onClickSortAlphabetically,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen()
            state.isEmpty -> EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )

            else ->
                FeedOrderContent(
                    feeds = state.items ?: emptyList(),
                    lazyListState = lazyListState,
                    paddingValues = paddingValues + topSmallPaddingValues +
                        PaddingValues(horizontal = MaterialTheme.padding.medium),
                    onClickDelete = onClickDelete,
                    onMoveUp = onClickMoveUp,
                    onMoveDown = onClickMoveDown,
                )
        }
    }
}

@Composable
fun FeedOrderContent(
    feeds: List<FeedItemUI>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickDelete: (Feed) -> Unit,
    onMoveUp: (Feed) -> Unit,
    onMoveDown: (Feed) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        itemsIndexed(
            items = feeds,
            key = { _, feed -> "feed-${feed.feed.id}" },
        ) { index, feed ->
            FeedOrderListItem(
                modifier = Modifier.animateItem(),
                feed = feed,
                canMoveUp = index != 0,
                canMoveDown = index != feeds.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = { onClickDelete(feed.feed) },
            )
        }
    }
}
