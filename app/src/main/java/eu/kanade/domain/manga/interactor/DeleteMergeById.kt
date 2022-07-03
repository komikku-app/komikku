package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMergeRepository

class DeleteMergeById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long) {
        return mangaMergeRepository.deleteById(id)
    }
}
