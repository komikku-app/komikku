package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository

class CountFeedSavedSearchBySourceId(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): Long {
        return feedSavedSearchRepository.countBySourceId(sourceId)
    }
}
