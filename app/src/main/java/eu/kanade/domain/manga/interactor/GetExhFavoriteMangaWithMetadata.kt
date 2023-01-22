package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import tachiyomi.domain.manga.model.Manga

class GetExhFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaMetadataRepository.getExhFavoriteMangaWithMetadata()
    }
}
