package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

class GetMangaTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(mangaId: Long): MangaTaste? = repository.getMangaTaste(mangaId)

    fun subscribe(mangaId: Long): Flow<MangaTaste?> = repository.getMangaTasteAsFlow(mangaId)

    suspend fun await(source: Long, url: String): MangaTaste? = repository.getMangaTaste(source, url)

    fun subscribe(source: Long, url: String): Flow<MangaTaste?> = repository.getMangaTasteAsFlow(source, url)

    suspend fun awaitAll(): List<MangaTaste> = repository.getAllMangaTastes()

    fun subscribeAll(): Flow<List<MangaTaste>> = repository.getAllMangaTastesAsFlow()
}
