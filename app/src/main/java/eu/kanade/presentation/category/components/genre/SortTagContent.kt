package eu.kanade.presentation.category.components.genre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.SortTagState
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.tachiyomi.ui.category.genre.SortTagPresenter

@Composable
fun SortTagContent(
    state: SortTagState,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onMoveUp: (String, Int) -> Unit,
    onMoveDown: (String, Int) -> Unit,
) {
    val tags = state.tags
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(tags) { index, tag ->
            SortTagListItem(
                modifier = Modifier.animateItemPlacement(),
                tag = tag,
                index = index,
                canMoveUp = index != 0,
                canMoveDown = index != tags.lastIndex,
                onMoveUp = { onMoveUp(tag, index) },
                onMoveDown = { onMoveDown(tag, index) },
                onDelete = { state.dialog = SortTagPresenter.Dialog.Delete(tag) },
            )
        }
    }
}
