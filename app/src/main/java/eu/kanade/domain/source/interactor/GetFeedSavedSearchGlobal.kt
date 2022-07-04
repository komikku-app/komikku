package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import exh.savedsearches.models.FeedSavedSearch
import kotlinx.coroutines.flow.Flow

class GetFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): List<FeedSavedSearch> {
        return feedSavedSearchRepository.getGlobal()
    }

    fun subscribe(): Flow<List<FeedSavedSearch>> {
        return feedSavedSearchRepository.getGlobalAsFlow()
    }
}
