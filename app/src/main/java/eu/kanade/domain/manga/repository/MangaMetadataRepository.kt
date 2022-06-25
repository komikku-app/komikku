package eu.kanade.domain.manga.repository

import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.flow.Flow

interface MangaMetadataRepository {
    suspend fun getMetadataById(id: Long): SearchMetadata?

    suspend fun subscribeMetadataById(id: Long): Flow<SearchMetadata?>

    suspend fun getTagsById(id: Long): List<SearchTag>

    suspend fun subscribeTagsById(id: Long): Flow<List<SearchTag>>

    suspend fun getTitlesById(id: Long): List<SearchTitle>

    suspend fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>>
}
