package eu.kanade.presentation.category.components.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.LazyColumn

@Composable
fun SourceRepoContent(
    repos: List<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(repos) { repo ->
            SourceRepoListItem(
                modifier = Modifier.animateItemPlacement(),
                repo = repo,
                onDelete = { onClickDelete(repo) },
            )
        }
    }
}
