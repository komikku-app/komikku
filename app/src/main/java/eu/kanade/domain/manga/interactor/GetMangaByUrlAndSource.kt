package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class GetMangaByUrlAndSource(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(url: String, sourceId: Long): Manga? {
        return try {
            mangaRepository.getMangaByUrlAndSource(url, sourceId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(url: String, sourceId: Long): Flow<Manga?> {
        return mangaRepository.subscribeMangaByUrlAndSource(url, sourceId)
    }
}
