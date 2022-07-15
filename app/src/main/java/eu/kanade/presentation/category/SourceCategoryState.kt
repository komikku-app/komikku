package eu.kanade.presentation.category

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryPresenter

@Stable
interface SourceCategoryState {
    val isLoading: Boolean
    var dialog: SourceCategoryPresenter.Dialog?
    val categories: List<String>
    val isEmpty: Boolean
}

fun SourceCategoryState(): SourceCategoryState {
    return SourceCategoryStateImpl()
}

class SourceCategoryStateImpl : SourceCategoryState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var dialog: SourceCategoryPresenter.Dialog? by mutableStateOf(null)
    override var categories: List<String> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { categories.isEmpty() }
}
