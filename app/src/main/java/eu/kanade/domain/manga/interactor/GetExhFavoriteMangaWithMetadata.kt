package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaMetadataRepository

class GetExhFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaMetadataRepository.getExhFavoriteMangaWithMetadata()
    }
}
