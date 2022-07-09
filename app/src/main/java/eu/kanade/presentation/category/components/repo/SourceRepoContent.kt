package eu.kanade.presentation.category.components.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun SourceRepoContent(
    repos: List<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(repos) { repo ->
            SourceRepoListItem(
                repo = repo,
                onDelete = onDelete,
            )
        }
    }
}
