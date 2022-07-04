package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import exh.savedsearches.models.SavedSearch

class GetSavedSearchBySourceIdFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<SavedSearch> {
        return feedSavedSearchRepository.getBySourceIdFeedSavedSearch(sourceId)
    }
}
