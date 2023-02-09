package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository

class GetMergedMangaById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long): List<Manga> {
        return try {
            mangaMergeRepository.getMergedMangaById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<Manga>> {
        return mangaMergeRepository.subscribeMergedMangaById(id)
    }
}
