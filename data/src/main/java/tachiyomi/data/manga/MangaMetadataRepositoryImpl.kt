package tachiyomi.data.manga

import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class MangaMetadataRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMetadataRepository {

    override suspend fun getMetadataById(id: Long): SearchMetadata? {
        return handler.awaitOneOrNull { search_metadataQueries.selectByMangaId(id, ::searchMetadataMapper) }
    }

    override suspend fun getMetadataByIds(ids: List<Long>): Map<Long, SearchMetadata> {
        return handler.awaitList { search_metadataQueries.selectByMangaIds(ids, ::searchMetadataMapper) }
            .associateBy { it.mangaId }
    }

    override fun subscribeMetadataById(id: Long): Flow<SearchMetadata?> {
        return handler.subscribeToOneOrNull { search_metadataQueries.selectByMangaId(id, ::searchMetadataMapper) }
    }

    override suspend fun getTagsById(id: Long): List<SearchTag> {
        return handler.awaitList { search_tagsQueries.selectByMangaId(id, ::searchTagMapper) }
    }

    override suspend fun getTagsByIds(ids: List<Long>): Map<Long, List<SearchTag>> {
        return handler.awaitList { search_tagsQueries.selectByMangaIds(ids, ::searchTagMapper) }
            .groupBy { it.mangaId }
    }

    override fun subscribeTagsById(id: Long): Flow<List<SearchTag>> {
        return handler.subscribeToList { search_tagsQueries.selectByMangaId(id, ::searchTagMapper) }
    }

    override suspend fun getTitlesById(id: Long): List<SearchTitle> {
        return handler.awaitList { search_titlesQueries.selectByMangaId(id, ::searchTitleMapper) }
    }

    override suspend fun getTitlesByIds(ids: List<Long>): Map<Long, List<SearchTitle>> {
        return handler.awaitList { search_titlesQueries.selectByMangaIds(ids, ::searchTitleMapper) }
            .groupBy { it.mangaId }
    }

    override fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>> {
        return handler.subscribeToList { search_titlesQueries.selectByMangaId(id, ::searchTitleMapper) }
    }

    override suspend fun insertFlatMetadata(flatMetadata: FlatMetadata) {
        require(flatMetadata.metadata.mangaId != -1L)

        handler.await(true) {
            flatMetadata.metadata.run {
                search_metadataQueries.upsert(mangaId, uploader, extra, indexedExtra, extraVersion.toLong())
            }
            search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.tags.forEach {
                search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type.toLong())
            }
            search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.titles.forEach {
                search_titlesQueries.insert(it.mangaId, it.title, it.type.toLong())
            }
        }
    }

    override suspend fun insertFlatMetadatas(flatMetadatas: List<FlatMetadata>) {
        handler.await(true) {
            flatMetadatas.filter { it.metadata.mangaId != -1L }
                .forEach { flatMetadata ->
                    search_metadataQueries.upsert(
                        flatMetadata.metadata.mangaId,
                        flatMetadata.metadata.uploader,
                        flatMetadata.metadata.extra,
                        flatMetadata.metadata.indexedExtra,
                        flatMetadata.metadata.extraVersion.toLong(),
                    )
                    search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
                    flatMetadata.tags.forEach {
                        search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type.toLong())
                    }
                    search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
                    flatMetadata.titles.forEach {
                        search_titlesQueries.insert(it.mangaId, it.title, it.type.toLong())
                    }
                }
        }
    }

    override suspend fun getExhFavoriteMangaWithMetadata(): List<Manga> {
        return handler.awaitList {
            mangasQueries.getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, MangaMapper::mapManga)
        }
    }

    override suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long> {
        return handler.awaitList { mangasQueries.getIdsOfFavoriteMangaWithMetadata() }
    }

    override suspend fun getSearchMetadata(): List<SearchMetadata> {
        return handler.awaitList { search_metadataQueries.selectAll(::searchMetadataMapper) }
    }

    private fun searchMetadataMapper(
        mangaId: Long,
        uploader: String?,
        extra: String,
        indexedExtra: String?,
        extraVersion: Long,
    ): SearchMetadata {
        return SearchMetadata(
            mangaId = mangaId,
            uploader = uploader,
            extra = extra,
            indexedExtra = indexedExtra,
            extraVersion = extraVersion.toInt(),
        )
    }

    private fun searchTitleMapper(
        mangaId: Long,
        id: Long?,
        title: String,
        type: Long,
    ): SearchTitle {
        return SearchTitle(
            mangaId = mangaId,
            id = id,
            title = title,
            type = type.toInt(),
        )
    }

    private fun searchTagMapper(
        mangaId: Long,
        id: Long?,
        namespace: String?,
        name: String,
        type: Long,
    ): SearchTag {
        return SearchTag(
            mangaId = mangaId,
            id = id,
            namespace = namespace,
            name = name,
            type = type.toInt(),
        )
    }
}
