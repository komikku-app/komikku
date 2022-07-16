package eu.kanade.presentation.category.components.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.SourceRepoState
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.tachiyomi.ui.category.repos.RepoPresenter

@Composable
fun SourceRepoContent(
    state: SourceRepoState,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
) {
    val repos = state.repos
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(repos) { repo ->
            SourceRepoListItem(
                modifier = Modifier.animateItemPlacement(),
                repo = repo,
                onDelete = { state.dialog = RepoPresenter.Dialog.Delete(repo) },
            )
        }
    }
}
