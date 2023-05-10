package tachiyomi.domain.manga.interactor

import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository

class GetReadMangaNotInLibrary(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getReadMangaNotInLibrary()
    }
}
