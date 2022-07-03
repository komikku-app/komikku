package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import exh.metadata.sql.models.SearchMetadata

class GetSearchMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): SearchMetadata? {
        return mangaMetadataRepository.getMetadataById(mangaId)
    }

    suspend fun await(): List<SearchMetadata> {
        return mangaMetadataRepository.getSearchMetadata()
    }
}
