package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.searchMetadataMapper
import eu.kanade.data.exh.searchTagMapper
import eu.kanade.data.exh.searchTitleMapper
import eu.kanade.domain.manga.repository.MangaMetadataRepository
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.flow.Flow

class MangaMetadataRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMetadataRepository {

    override suspend fun getMetadataById(id: Long): SearchMetadata? {
        return handler.awaitOneOrNull { search_metadataQueries.selectByMangaId(id, searchMetadataMapper) }
    }

    override suspend fun subscribeMetadataById(id: Long): Flow<SearchMetadata?> {
        return handler.subscribeToOneOrNull { search_metadataQueries.selectByMangaId(id, searchMetadataMapper) }
    }

    override suspend fun getTagsById(id: Long): List<SearchTag> {
        return handler.awaitList { search_tagsQueries.selectByMangaId(id, searchTagMapper) }
    }

    override suspend fun subscribeTagsById(id: Long): Flow<List<SearchTag>> {
        return handler.subscribeToList { search_tagsQueries.selectByMangaId(id, searchTagMapper) }
    }

    override suspend fun getTitlesById(id: Long): List<SearchTitle> {
        return handler.awaitList { search_titlesQueries.selectByMangaId(id, searchTitleMapper) }
    }

    override suspend fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>> {
        return handler.subscribeToList { search_titlesQueries.selectByMangaId(id, searchTitleMapper) }
    }
}
