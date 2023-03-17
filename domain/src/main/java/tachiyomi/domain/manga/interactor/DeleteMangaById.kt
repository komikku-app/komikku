package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class DeleteMangaById(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long) {
        return mangaRepository.deleteManga(id)
    }
}
