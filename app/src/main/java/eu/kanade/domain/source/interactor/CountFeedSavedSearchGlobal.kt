package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository

class CountFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): Long {
        return feedSavedSearchRepository.countGlobal()
    }
}
