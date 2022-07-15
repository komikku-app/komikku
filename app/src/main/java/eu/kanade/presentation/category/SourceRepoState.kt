package eu.kanade.presentation.category

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.category.repos.RepoPresenter

@Stable
interface SourceRepoState {
    val isLoading: Boolean
    var dialog: RepoPresenter.Dialog?
    val repos: List<String>
    val isEmpty: Boolean
}

fun SourceRepoState(): SourceRepoState {
    return SourceRepoStateImpl()
}

class SourceRepoStateImpl : SourceRepoState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var dialog: RepoPresenter.Dialog? by mutableStateOf(null)
    override var repos: List<String> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { repos.isEmpty() }
}
