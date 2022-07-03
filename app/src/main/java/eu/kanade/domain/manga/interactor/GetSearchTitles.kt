package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import exh.metadata.sql.models.SearchTitle

class GetSearchTitles(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTitle> {
        return mangaMetadataRepository.getTitlesById(mangaId)
    }
}
