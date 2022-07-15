package eu.kanade.presentation.category.components.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.SourceCategoryState
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryPresenter

@Composable
fun SourceCategoryContent(
    state: SourceCategoryState,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
) {
    val categories = state.categories
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            SourceCategoryListItem(
                modifier = Modifier.animateItemPlacement(),
                category = category,
                onRename = { state.dialog = SourceCategoryPresenter.Dialog.Rename(category) },
                onDelete = { state.dialog = SourceCategoryPresenter.Dialog.Delete(category) },
            )
        }
    }
}
