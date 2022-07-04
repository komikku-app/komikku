package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import exh.savedsearches.models.FeedSavedSearch
import kotlinx.coroutines.flow.Flow

class GetFeedSavedSearchBySourceId(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<FeedSavedSearch> {
        return feedSavedSearchRepository.getBySourceId(sourceId)
    }

    fun subscribe(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return feedSavedSearchRepository.getBySourceIdAsFlow(sourceId)
    }
}
