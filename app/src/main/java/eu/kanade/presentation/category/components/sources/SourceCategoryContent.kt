package eu.kanade.presentation.category.components.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.LazyColumn

@Composable
fun SourceCategoryContent(
    categories: List<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, category ->
            SourceCategoryListItem(
                category = category,
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}
