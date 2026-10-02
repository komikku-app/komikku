package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.MangaTaste

interface TasteRepository {
    suspend fun getMangaTaste(mangaId: Long): MangaTaste?

    fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?>

    suspend fun getMangaTaste(source: Long, url: String): MangaTaste?

    fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?>

    suspend fun getAllMangaTastes(): List<MangaTaste>

    fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>>

    suspend fun upsertMangaTaste(taste: MangaTaste)

    suspend fun deleteMangaTaste(mangaId: Long)

    suspend fun deleteAllMangaTastes()
}
