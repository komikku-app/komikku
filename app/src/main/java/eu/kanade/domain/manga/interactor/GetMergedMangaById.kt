package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaMergeRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

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
