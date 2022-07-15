package eu.kanade.presentation.category

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.category.genre.SortTagPresenter

@Stable
interface SortTagState {
    val isLoading: Boolean
    var dialog: SortTagPresenter.Dialog?
    val tags: List<String>
    val isEmpty: Boolean
}

fun SortTagState(): SortTagState {
    return SortTagStateImpl()
}

class SortTagStateImpl : SortTagState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var dialog: SortTagPresenter.Dialog? by mutableStateOf(null)
    override var tags: List<String> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { tags.isEmpty() }
}
