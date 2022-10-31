package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.logcat
import exh.merged.sql.models.MergedMangaReference
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import logcat.LogPriority

class GetMergedChapterByMangaId(
    private val chapterRepository: ChapterRepository,
    private val getMergedReferencesById: GetMergedReferencesById,
    private val sourceManager: SourceManager,
) {

    suspend fun await(mangaId: Long, editScanlators: Boolean = false, dedupe: Boolean = true): List<Chapter> {
        return transformMergedChapters(mangaId, getFromDatabase(mangaId), editScanlators, dedupe)
    }

    suspend fun subscribe(mangaId: Long, editScanlators: Boolean = false, dedupe: Boolean = true): Flow<List<Chapter>> {
        return try {
            chapterRepository.getMergedChapterByMangaIdAsFlow(mangaId)
                .map {
                    transformMergedChapters(mangaId, it, editScanlators, dedupe)
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
    suspend fun transformMergedChapters(mangaId: Long, chapterList: List<Chapter>, editScanlators: Boolean, dedupe: Boolean): List<Chapter> {
        val mangaReferences = getMergedReferencesById.await(mangaId)
        val chapters = if (editScanlators) {
            val sources = mangaReferences.map { sourceManager.getOrStub(it.mangaSourceId) to it.mangaId }
            chapterList.map { chapter ->
                val source = sources.firstOrNull { chapter.mangaId == it.second }?.first
                if (source != null) {
                    chapter.copy(
                        scanlator = if (chapter.scanlator.isNullOrBlank()) {
                            source.name
                        } else {
                            "$source: ${chapter.scanlator}"
                        },
                    )
                } else {
                    chapter
                }
            }
        } else {
            chapterList
        }
        return if (dedupe) dedupeChapterList(mangaReferences, chapters) else chapters
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
