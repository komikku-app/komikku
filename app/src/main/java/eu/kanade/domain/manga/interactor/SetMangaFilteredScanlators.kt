package eu.kanade.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaFilteredScanlators(private val mangaRepository: MangaRepository) {

    suspend fun awaitSetFilteredScanlators(manga: Manga, filteredScanlators: List<String>): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                filteredScanlators = filteredScanlators,
            ),
        )
    }
}
