package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
interface SourceFeedState {
    val isLoading: Boolean
    var searchQuery: String?
    val items: List<SourceFeedUI>?
}

fun SourceFeedState(): SourceFeedState {
    return SourceFeedStateImpl()
}

class SourceFeedStateImpl : SourceFeedState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var searchQuery: String? by mutableStateOf(null)
    override var items: List<SourceFeedUI>? by mutableStateOf(null)
}
