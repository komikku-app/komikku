package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.model.Manga

class GetMangaBySource(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(sourceId: Long): List<Manga> {
        return mangaRepository.getMangaBySourceId(sourceId)
    }
}
