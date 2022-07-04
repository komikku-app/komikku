package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SavedSearchRepository
import exh.savedsearches.models.SavedSearch

class GetSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearchId: Long): SavedSearch {
        return savedSearchRepository.getById(savedSearchId)!!
    }

    suspend fun awaitOrNull(savedSearchId: Long): SavedSearch? {
        return savedSearchRepository.getById(savedSearchId)
    }
}
