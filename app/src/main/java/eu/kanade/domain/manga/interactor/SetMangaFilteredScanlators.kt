package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.repository.MangaRepository

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
