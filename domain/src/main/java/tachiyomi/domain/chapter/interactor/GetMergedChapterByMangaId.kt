package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.MergedMangaReference

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

    fun transformMergedChapters(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>, dedupe: Boolean): List<Chapter> {
        return if (dedupe) dedupeChapterList(mangaReferences, chapterList) else chapterList
    }

    private fun dedupeChapterList(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }?.chapterSortMode) {
            MergedMangaReference.CHAPTER_SORT_NO_DEDUPE, MergedMangaReference.CHAPTER_SORT_NONE -> chapterList
            MergedMangaReference.CHAPTER_SORT_PRIORITY -> dedupeByPriority(mangaReferences, chapterList)
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

    private fun dedupeByPriority(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>): List<Chapter> {
        val sortedChapterList = mutableListOf<Chapter>()

        var existingChapterIndex: Int
        chapterList.groupBy { it.mangaId }
            .entries
            .sortedBy { (mangaId) ->
                mangaReferences.find { it.mangaId == mangaId }?.chapterPriority ?: Int.MAX_VALUE
            }
            .forEach { (_, chapters) ->
                existingChapterIndex = -1
                chapters.forEach { chapter ->
                    val oldChapterIndex = existingChapterIndex
                    if (chapter.isRecognizedNumber) {
                        existingChapterIndex = sortedChapterList.indexOfFirst {
                            it.isRecognizedNumber && it.chapterNumber == chapter.chapterNumber && // check if the chapter is not already there
                                it.mangaId != chapter.mangaId // allow multiple chapters of the same number from the same source
                        }
                        if (existingChapterIndex == -1) {
                            sortedChapterList.add(oldChapterIndex + 1, chapter)
                            existingChapterIndex = oldChapterIndex + 1
                        }
                    } else {
                        sortedChapterList.add(oldChapterIndex + 1, chapter)
                        existingChapterIndex = oldChapterIndex + 1
                    }
                }
            }

        return sortedChapterList.mapIndexed { index, chapter ->
            chapter.copy(sourceOrder = index.toLong())
        }
    }
}
