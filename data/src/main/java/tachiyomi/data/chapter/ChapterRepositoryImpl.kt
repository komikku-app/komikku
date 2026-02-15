package tachiyomi.data.chapter

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga

class ChapterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
                    chaptersQueries.insert(
                        chapter.mangaId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.read,
                        chapter.bookmark,
                        chapter.lastPageRead,
                        chapter.chapterNumber,
                        chapter.sourceOrder,
                        chapter.dateFetch,
                        chapter.dateUpload,
                        chapter.version,
                    )
                    val lastInsertId = chaptersQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { chapterUpdate ->
                chaptersQueries.update(
                    mangaId = chapterUpdate.mangaId,
                    url = chapterUpdate.url,
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    read = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    lastPageRead = chapterUpdate.lastPageRead,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateFetch = chapterUpdate.dateFetch,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                    version = chapterUpdate.version,
                    isSyncing = 0,
                    deleted = chapterUpdate.deleted,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { chaptersQueries.removeChaptersWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun softDeleteChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { chaptersQueries.softDeleteChaptersWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(
        mangaId: Long,
        applyFilter: Boolean,
        includeDeleted: Boolean,
    ): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getChaptersByMangaId(
                mangaId = mangaId,
                // IMPORTANT: use named args to avoid ordering issues in generated signature
                includeDeleted = includeDeleted.toLong(),
                applyFilter = applyFilter.toLong(),
                // KMK -->
                bookmarkUnmask = Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                bookmarkMask = Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                mapper = ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return handler.awaitList {
            chaptersQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            chaptersQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getBookmarkedChaptersByMangaId(
                mangaId,
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return handler.awaitOneOrNull { chaptersQueries.getChapterById(id, ChapterMapper::mapChapter) }
    }

    override suspend fun getChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean,
        includeDeleted: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            chaptersQueries.getChaptersByMangaId(
                mangaId = mangaId,
                // IMPORTANT: use named args to avoid ordering issues in generated signature
                includeDeleted = includeDeleted.toLong(),
                applyFilter = applyFilter.toLong(),
                // KMK -->
                bookmarkUnmask = Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                bookmarkMask = Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                mapper = ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long, includeDeleted: Boolean): Chapter? {
        return handler.awaitOneOrNull {
            chaptersQueries.getChapterByUrlAndMangaId(
                url,
                mangaId,
                includeDeleted.toLong(),
                ChapterMapper::mapChapter,
            )
        }
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return handler.awaitList { chaptersQueries.getChapterByUrl(url, ChapterMapper::mapChapter) }
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getMergedChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            chaptersQueries.getMergedChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return handler.awaitList {
            chaptersQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            chaptersQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }
    // SY <--
}
