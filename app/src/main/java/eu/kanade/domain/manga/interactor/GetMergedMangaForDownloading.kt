package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.model.Manga

class GetMergedMangaForDownloading(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(mergeId: Long): List<Manga> {
        return mangaMergeRepository.getMergeMangaForDownloading(mergeId)
    }
}
