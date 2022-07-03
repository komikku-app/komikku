package exh.metadata.metadata.base

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.searchMetadataMapper
import eu.kanade.data.exh.searchTagMapper
import eu.kanade.data.exh.searchTitleMapper
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@Serializable
data class FlatMetadata(
    val metadata: SearchMetadata,
    val tags: List<SearchTag>,
    val titles: List<SearchTitle>,
) {
    inline fun <reified T : RaisedSearchMetadata> raise(): T = raise(T::class)

    @OptIn(InternalSerializationApi::class)
    fun <T : RaisedSearchMetadata> raise(clazz: KClass<T>): T =
        RaisedSearchMetadata.raiseFlattenJson
            .decodeFromString(clazz.serializer(), metadata.extra).apply {
                fillBaseFields(this@FlatMetadata)
            }
}

@Deprecated("Replace with GetFlatMetadataById")
suspend fun DatabaseHandler.awaitFlatMetadataForManga(mangaId: Long): FlatMetadata? {
    return await {
        val meta = search_metadataQueries.selectByMangaId(mangaId, searchMetadataMapper).executeAsOneOrNull()
        if (meta != null) {
            val tags = search_tagsQueries.selectByMangaId(mangaId, searchTagMapper).executeAsList()
            val titles = search_titlesQueries.selectByMangaId(mangaId, searchTitleMapper).executeAsList()

            FlatMetadata(meta, tags, titles)
        } else null
    }
}

@Deprecated("Replace with InsertFlatMetadata")
suspend fun DatabaseHandler.awaitInsertFlatMetadata(flatMetadata: FlatMetadata) {
    require(flatMetadata.metadata.mangaId != -1L)

    await(true) {
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
