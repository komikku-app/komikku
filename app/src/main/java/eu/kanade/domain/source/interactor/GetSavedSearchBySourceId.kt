package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SavedSearchRepository
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.flow.Flow

class GetSavedSearchBySourceId(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<SavedSearch> {
        return savedSearchRepository.getBySourceId(sourceId)
    }

    fun subscribe(sourceId: Long): Flow<List<SavedSearch>> {
        return savedSearchRepository.getBySourceIdAsFlow(sourceId)
    }
}
