package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class SwapFeedOrder(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun swapOrder(feed1: FeedSavedSearch, feed2: FeedSavedSearch) {
        return feedSavedSearchRepository.swapOrder(feed1, feed2)
    }

    suspend fun moveToBottom(feed: FeedSavedSearch) {
        return feedSavedSearchRepository.moveToBottom(feed)
    }
}
