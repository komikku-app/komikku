package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository

class GetMangaBySource(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(sourceId: Long): List<Manga> {
        return mangaRepository.getMangaBySource(sourceId)
    }
}
