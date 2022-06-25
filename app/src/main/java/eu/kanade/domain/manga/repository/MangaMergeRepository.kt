package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import exh.merged.sql.models.MergedMangaReference
import kotlinx.coroutines.flow.Flow

interface MangaMergeRepository {
    suspend fun getMergedMangaById(id: Long): List<Manga>

    suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>>

    suspend fun getReferencesById(id: Long): List<MergedMangaReference>

    suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>>
}
