package eu.kanade.presentation.category.components.genre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.LazyColumn

@Composable
fun SortTagContent(
    categories: List<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onMoveUp: (String, Int) -> Unit,
    onMoveDown: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, tag ->
            SortTagListItem(
                tag = tag,
                index = index,
                canMoveUp = index != 0,
                canMoveDown = index != categories.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete,
            )
        }
    }
}
