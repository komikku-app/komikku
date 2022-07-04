package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SavedSearchRepository

class DeleteSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearchId: Long) {
        return savedSearchRepository.delete(savedSearchId)
    }
}
