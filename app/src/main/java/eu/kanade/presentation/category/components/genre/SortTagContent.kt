package eu.kanade.presentation.category.components.genre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.components.material.padding

@Composable
fun SortTagContent(
    tags: ImmutableList<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickDelete: (String) -> Unit,
    onMoveUp: (String, Int) -> Unit,
    onMoveDown: (String, Int) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        itemsIndexed(tags, key = { _, tag -> tag }) { index, tag ->
            SortTagListItem(
                modifier = Modifier.animateItem(),
                tag = tag,
                canMoveUp = index != 0,
                canMoveDown = index != tags.lastIndex,
                onMoveUp = { onMoveUp(tag, index) },
                onMoveDown = { onMoveDown(tag, index) },
                onDelete = { onClickDelete(tag) },
            )
        }
    }
}
