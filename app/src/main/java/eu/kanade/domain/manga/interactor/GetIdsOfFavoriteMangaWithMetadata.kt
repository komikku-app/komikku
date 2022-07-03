package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository

class GetIdsOfFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Long> {
        return mangaMetadataRepository.getIdsOfFavoriteMangaWithMetadata()
    }
}
