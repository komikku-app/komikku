package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.repository.SavedSearchRepository

class DeleteSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearchId: Long) {
        return savedSearchRepository.delete(savedSearchId)
    }
}
