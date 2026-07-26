package tachiyomi.data.history

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga

class HistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : HistoryRepository {

    override fun getHistory(
        query: String,
        // KMK -->
        unfinishedManga: Boolean?,
        unfinishedChapter: Boolean?,
        nonLibraryEntries: Boolean?,
        // KMK <--
    ): Flow<List<HistoryWithRelations>> {
        return handler.subscribeToList {
            historyViewQueries.history(
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                unfinishedManga?.toLong(),
                unfinishedChapter,
                nonLibraryEntries,
                // KMK <--
                query,
                HistoryMapper::mapHistoryWithRelations,
            )
        }
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return handler.awaitOneOrNull {
            historyViewQueries.getLatestHistory(
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                HistoryMapper::mapHistoryWithRelations,
            )
        }
    }

    override suspend fun getTotalReadDuration(): Long {
        return handler.awaitOne { historyQueries.getReadDuration() }
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return handler.awaitList { historyQueries.getHistoryByMangaId(mangaId, HistoryMapper::mapHistory) }
    }

    // KMK -->
    override suspend fun resetHistory(historyIds: List<Long>) {
        try {
            handler.await(inTransaction = true) {
                rebuildingStats({ historyQueries.getMangaIdsByHistoryIds(historyIds).executeAsList() }) {
                    historyQueries.resetHistoryByIds(historyIds)
                }
            }
            // KMK <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    // KMK -->
    override suspend fun resetHistoryByMangaIds(mangaIds: List<Long>) {
        try {
            handler.await(inTransaction = true) {
                rebuildingStats({ mangaIds }) { historyQueries.resetHistoryByMangaIds(mangaIds) }
            }
            // KMK <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await(inTransaction = true) {
                // KMK -->
                rebuildingStats({ historyQueries.getMangaIdsWithHistory().executeAsList() }) {
                    historyQueries.removeAllHistory()
                }
                // KMK <--
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    // KMK -->
    override suspend fun removeResettedHistory() {
        try {
            handler.await(inTransaction = true) {
                rebuildingStats({ historyQueries.getMangaIdsWithResettedHistory().executeAsList() }) {
                    historyQueries.removeResettedHistory()
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    /**
     * Drops the cached stats rows for the affected entries so the per-row history triggers
     * become no-ops, then rebuilds them once. [resolveMangaIds] runs before [block], while
     * the history rows can still be joined back to their manga.
     */
    private fun Database.rebuildingStats(resolveMangaIds: () -> List<Long>, block: Database.() -> Unit) {
        val mangaIds = resolveMangaIds()
        if (mangaIds.isEmpty()) {
            block()
            return
        }
        manga_chapter_statsQueries.deleteForMangaIds(mangaIds)
        block()
        manga_chapter_statsQueries.rebuildForMangaIds(mangaIds)
    }
    // KMK <--

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        try {
            handler.await {
                historyQueries.upsert(
                    historyUpdate.chapterId,
                    historyUpdate.readAt,
                    historyUpdate.sessionReadDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    // SY -->
    override suspend fun upsertHistory(historyUpdates: List<HistoryUpdate>) {
        try {
            handler.await(true) {
                historyUpdates.forEach { historyUpdate ->
                    historyQueries.upsert(
                        historyUpdate.chapterId,
                        historyUpdate.readAt,
                        historyUpdate.sessionReadDuration,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
    // SY <--
}
