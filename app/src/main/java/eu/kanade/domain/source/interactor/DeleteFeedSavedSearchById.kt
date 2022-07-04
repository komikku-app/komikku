package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository

class DeleteFeedSavedSearchById(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(feedSavedSearchId: Long) {
        return feedSavedSearchRepository.delete(feedSavedSearchId)
    }
}
