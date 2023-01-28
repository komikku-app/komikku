package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.lang.toLong
import tachiyomi.core.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

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
                    chapterSortMode = value.chapterSortMode?.toLong(),
                )
            }
        }
    }

    override suspend fun insert(reference: MergedMangaReference): Long? {
        return handler.awaitOneOrNull {
            mergedQueries.insert(
                infoManga = reference.isInfoManga,
                getChapterUpdates = reference.getChapterUpdates,
                chapterSortMode = reference.chapterSortMode.toLong(),
                chapterPriority = reference.chapterPriority.toLong(),
                downloadChapters = reference.downloadChapters,
                mergeId = reference.mergeId!!,
                mergeUrl = reference.mergeUrl,
                mangaId = reference.mangaId,
                mangaUrl = reference.mangaUrl,
                mangaSource = reference.mangaSourceId,
            )
            mergedQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(references: List<MergedMangaReference>) {
        handler.await(true) {
            references.forEach { reference ->
                mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    mangaId = reference.mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await {
            mergedQueries.deleteById(id)
        }
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        handler.await {
            mergedQueries.deleteByMergeId(mergeId)
        }
    }

    override suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga> {
        return handler.awaitList { mergedQueries.selectMergedMangasForDownloadingById(mergeId, mangaMapper) }
    }
}
