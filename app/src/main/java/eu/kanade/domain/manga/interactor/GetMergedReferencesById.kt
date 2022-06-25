package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMergeRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.merged.sql.models.MergedMangaReference
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

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
