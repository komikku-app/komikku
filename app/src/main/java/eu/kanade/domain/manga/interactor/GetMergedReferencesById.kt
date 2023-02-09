package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

class GetMergedReferencesById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long): List<MergedMangaReference> {
        return try {
            mangaMergeRepository.getReferencesById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<MergedMangaReference>> {
        return mangaMergeRepository.subscribeReferencesById(id)
    }
}
