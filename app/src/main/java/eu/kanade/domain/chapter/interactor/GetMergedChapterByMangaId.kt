package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.tachiyomi.util.system.logcat
import exh.merged.sql.models.MergedMangaReference
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import logcat.LogPriority

class GetMergedChapterByMangaId(
    private val chapterRepository: ChapterRepository,
    private val getMergedReferencesById: GetMergedReferencesById,
) {

    suspend fun await(mangaId: Long, dedupe: Boolean = true): List<Chapter> {
        return transformMergedChapters(getMergedReferencesById.await(mangaId), getFromDatabase(mangaId), dedupe)
    }

    suspend fun subscribe(mangaId: Long, dedupe: Boolean = true): Flow<List<Chapter>> {
        return try {
            chapterRepository.getMergedChapterByMangaIdAsFlow(mangaId)
                .combine(getMergedReferencesById.subscribe(mangaId)) { chapters, references ->
                    transformMergedChapters(references, chapters, dedupe)
                }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            flowOf(emptyList())
        }
    }

    private suspend fun getFromDatabase(mangaId: Long): List<Chapter> {
        return try {
            chapterRepository.getMergedChapterByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    // TODO more chapter dedupe
    fun transformMergedChapters(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>, dedupe: Boolean): List<Chapter> {
        return if (dedupe) dedupeChapterList(mangaReferences, chapterList) else chapterList
    }

    private fun dedupeChapterList(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }?.chapterSortMode) {
            MergedMangaReference.CHAPTER_SORT_NO_DEDUPE, MergedMangaReference.CHAPTER_SORT_NONE -> chapterList
            MergedMangaReference.CHAPTER_SORT_PRIORITY -> chapterList
            MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> {
                findSourceWithMostChapters(chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> {
                findSourceWithHighestChapterNumber(chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            else -> chapterList
        }
    }

    private fun findSourceWithMostChapters(chapterList: List<Chapter>): Long? {
        return chapterList.groupBy { it.mangaId }.maxByOrNull { it.value.size }?.key
    }

    private fun findSourceWithHighestChapterNumber(chapterList: List<Chapter>): Long? {
        return chapterList.maxByOrNull { it.chapterNumber }?.mangaId
    }
}
