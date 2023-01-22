package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.model.Manga

class GetAllManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaRepository.getAll()
    }
}
