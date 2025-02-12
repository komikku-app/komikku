package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetMangaBySource(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(sourceId: Long): List<Manga> {
        return mangaRepository.getMangaBySourceId(sourceId)
    }

    suspend fun await(sourceIds: List<Long>): Map<Long, List<Manga>> {
        return mangaRepository.getMangaBySourceIds(sourceIds)
    }
}
