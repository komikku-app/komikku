package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.mergedMangaReferenceMapper
import eu.kanade.data.toLong
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MergeMangaSettingsUpdate
import eu.kanade.domain.manga.repository.MangaMergeRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.merged.sql.models.MergedMangaReference
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class MangaMergeRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMergeRepository {

    override suspend fun getMergedManga(): List<Manga> {
        return handler.awaitList { mergedQueries.selectAllMergedMangas(mangaMapper) }
    }

    override suspend fun subscribeMergedManga(): Flow<List<Manga>> {
        return handler.subscribeToList { mergedQueries.selectAllMergedMangas(mangaMapper) }
    }

    override suspend fun getMergedMangaById(id: Long): List<Manga> {
        return handler.awaitList { mergedQueries.selectMergedMangasById(id, mangaMapper) }
    }

    override suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mergedQueries.selectMergedMangasById(id, mangaMapper) }
    }

    override suspend fun getReferencesById(id: Long): List<MergedMangaReference> {
        return handler.awaitList { mergedQueries.selectByMergeId(id, mergedMangaReferenceMapper) }
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>> {
        return handler.subscribeToList { mergedQueries.selectByMergeId(id, mergedMangaReferenceMapper) }
    }

    override suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean {
        return try {
            partialUpdate(*values.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg values: MergeMangaSettingsUpdate) {
        handler.await(inTransaction = true) {
            values.forEach { value ->
                mergedQueries.updateSettingsById(
                    id = value.id,
                    getChapterUpdates = value.getChapterUpdates?.toLong(),
                    downloadChapters = value.downloadChapters?.toLong(),
                    infoManga = value.isInfoManga?.toLong(),
                    chapterPriority = value.chapterPriority?.toLong(),
                )
            }
        }
    }
}
