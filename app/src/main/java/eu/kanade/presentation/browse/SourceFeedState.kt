package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.toItems

@Stable
interface SourceFeedState {
    val isLoading: Boolean
    var searchQuery: String?
    val filters: FilterList
    val filterItems: List<IFlexible<*>>
    val items: List<SourceFeedUI>?
}

fun SourceFeedState(): SourceFeedState {
    return SourceFeedStateImpl()
}

class SourceFeedStateImpl : SourceFeedState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var searchQuery: String? by mutableStateOf(null)
    override var filters: FilterList by mutableStateOf(FilterList())
    override val filterItems: List<IFlexible<*>> by derivedStateOf { filters.toItems() }
    override var items: List<SourceFeedUI>? by mutableStateOf(null)
}
