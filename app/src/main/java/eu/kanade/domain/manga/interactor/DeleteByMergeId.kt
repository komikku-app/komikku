package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMergeRepository

class DeleteByMergeId(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long) {
        return mangaMergeRepository.deleteByMergeId(id)
    }
}
