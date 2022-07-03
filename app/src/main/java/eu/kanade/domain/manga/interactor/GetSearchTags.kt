package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import exh.metadata.sql.models.SearchTag

class GetSearchTags(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTag> {
        return mangaMetadataRepository.getTagsById(mangaId)
    }
}
