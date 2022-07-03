package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMergeRepository
import exh.merged.sql.models.MergedMangaReference

class InsertMergedReference(
    private val mangaMergedRepository: MangaMergeRepository,
) {

    suspend fun await(reference: MergedMangaReference): Long? {
        return mangaMergedRepository.insert(reference)
    }

    suspend fun awaitAll(references: List<MergedMangaReference>) {
        mangaMergedRepository.insertAll(references)
    }
}
