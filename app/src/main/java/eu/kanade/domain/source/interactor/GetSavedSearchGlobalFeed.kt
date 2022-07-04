package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import exh.savedsearches.models.SavedSearch

class GetSavedSearchGlobalFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): List<SavedSearch> {
        return feedSavedSearchRepository.getGlobalFeedSavedSearch()
    }
}
