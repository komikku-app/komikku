package tachiyomi.data.episode

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.episode.model.Chapter
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

class EpisodeRepositoryImpl(
    private val handler: DatabaseHandler,
) : EpisodeRepository {

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

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        handler.await(inTransaction = true) {
            episodeUpdates.forEach { chapterUpdate ->
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

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getChaptersByMangaId(mangaId, applyScanlatorFilter.toLong(), EpisodeMapper::mapChapter)
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
                EpisodeMapper::mapChapter,
            )
        }
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return handler.awaitOneOrNull { chaptersQueries.getChapterById(id, EpisodeMapper::mapChapter) }
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return handler.subscribeToList {
            chaptersQueries.getChaptersByMangaId(mangaId, applyScanlatorFilter.toLong(), EpisodeMapper::mapChapter)
        }
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return handler.awaitOneOrNull {
            chaptersQueries.getChapterByUrlAndMangaId(
                url,
                mangaId,
                EpisodeMapper::mapChapter,
            )
        }
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return handler.awaitList { chaptersQueries.getChapterByUrl(url, EpisodeMapper::mapChapter) }
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getMergedChaptersByMangaId(
                mangaId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapChapter,
            )
        }
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            chaptersQueries.getMergedChaptersByMangaId(
                mangaId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapChapter,
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
