package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.browse.feed.FeedPresenter

@Stable
interface FeedState {
    var dialog: FeedPresenter.Dialog?
    val isLoading: Boolean
    val isEmpty: Boolean
    val items: List<FeedItemUI>?
}

fun FeedState(): FeedState {
    return FeedStateImpl()
}

class FeedStateImpl : FeedState {
    override var dialog: FeedPresenter.Dialog? by mutableStateOf(null)
    override var isLoading: Boolean by mutableStateOf(true)
    override var isEmpty: Boolean by mutableStateOf(false)
    override var items: List<FeedItemUI>? by mutableStateOf(null)
}
