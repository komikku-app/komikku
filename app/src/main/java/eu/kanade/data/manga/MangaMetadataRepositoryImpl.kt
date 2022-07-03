package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.searchMetadataMapper
import eu.kanade.data.exh.searchTagMapper
import eu.kanade.data.exh.searchTitleMapper
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaMetadataRepository
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
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

    override suspend fun insertFlatMetadata(flatMetadata: FlatMetadata) {
        require(flatMetadata.metadata.mangaId != -1L)

        handler.await(true) {
            flatMetadata.metadata.run {
                search_metadataQueries.upsert(mangaId, uploader, extra, indexedExtra, extraVersion)
            }
            search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.tags.forEach {
                search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type)
            }
            search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.titles.forEach {
                search_titlesQueries.insert(it.mangaId, it.title, it.type)
            }
        }
    }

    override suspend fun getExhFavoriteMangaWithMetadata(): List<Manga> {
        return handler.awaitList { mangasQueries.getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, mangaMapper) }
    }

    override suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long> {
        return handler.awaitList { mangasQueries.getIdsOfFavoriteMangaWithMetadata() }
    }

    override suspend fun getSearchMetadata(): List<SearchMetadata> {
        return handler.awaitList { search_metadataQueries.selectAll(searchMetadataMapper) }
    }
}
