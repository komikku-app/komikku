package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MergeMangaSettingsUpdate
import exh.merged.sql.models.MergedMangaReference
import kotlinx.coroutines.flow.Flow

interface MangaMergeRepository {
    suspend fun getMergedManga(): List<Manga>

    suspend fun subscribeMergedManga(): Flow<List<Manga>>

    suspend fun getMergedMangaById(id: Long): List<Manga>

    suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>>

    suspend fun getReferencesById(id: Long): List<MergedMangaReference>

    suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>>

    suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean

    suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean

    suspend fun insert(reference: MergedMangaReference): Long?

    suspend fun insertAll(references: List<MergedMangaReference>)

    suspend fun deleteById(id: Long)

    suspend fun deleteByMergeId(mergeId: Long)

    suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga>
}
