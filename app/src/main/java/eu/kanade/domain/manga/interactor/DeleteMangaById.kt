package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository

class DeleteMangaById(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long) {
        return mangaRepository.deleteManga(id)
    }
}
