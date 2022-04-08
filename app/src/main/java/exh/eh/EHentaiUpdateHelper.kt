package exh.eh

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import exh.util.executeOnIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uy.kohesive.injekt.injectLazy
import java.io.File

data class ChapterChain(val manga: Manga, val chapters: List<Chapter>, val history: List<History>)

class EHentaiUpdateHelper(context: Context) {
    val parentLookupTable =
        MemAutoFlushingLookupTable(
            File(context.filesDir, "exh-plt.maftable"),
            GalleryEntry.Serializer(),
        )
    private val db: DatabaseHelper by injectLazy()

    /**
     * @param chapters Cannot be an empty list!
     *
     * @return Triple<Accepted, Discarded, HasNew>
     */
    fun findAcceptedRootAndDiscardOthers(sourceId: Long, chapters: List<Chapter>): Flow<Triple<ChapterChain, List<ChapterChain>, Boolean>> {
        // Find other chains
        val chainsFlow = flowOf(chapters)
            .map { chapterList ->
                chapterList.flatMap { chapter ->
                    db.getChapters(chapter.url).executeOnIO().mapNotNull { it.manga_id }
                }.distinct()
            }
            .map { mangaIds ->
                mangaIds
                    .mapNotNull { mangaId ->
                        coroutineScope {
                            val manga = async(Dispatchers.IO) {
                                db.getManga(mangaId).executeAsBlocking()
                            }
                            val chapterList = async(Dispatchers.IO) {
                                db.getChapters(mangaId).executeAsBlocking()
                            }
                            val history = async(Dispatchers.IO) {
                                db.getHistoryByMangaId(mangaId).executeAsBlocking()
                            }
                            ChapterChain(
                                manga.await() ?: return@coroutineScope null,
                                chapterList.await(),
                                history.await(),
                            )
                        }
                    }
                    .filter { it.manga.source == sourceId }
            }

        // Accept oldest chain
        val chainsWithAccepted = chainsFlow.map { chains ->
            val acceptedChain = chains.minByOrNull { it.manga.id!! }!!

            acceptedChain to chains
        }

        return chainsWithAccepted.map { (accepted, chains) ->
            val toDiscard = chains.filter { it.manga.favorite && it.manga.id != accepted.manga.id }

            val chainsAsChapters = chains.flatMap { it.chapters }
            val chainsAsHistory = chains.flatMap { it.history }

            if (toDiscard.isNotEmpty()) {
                // Copy chain chapters to curChapters
                val (newChapters, new) = getChapterList(accepted, toDiscard, chainsAsChapters)
                val (history, urlHistory, deleteHistory) = getHistory(newChapters, chainsAsChapters, chainsAsHistory)

                toDiscard.forEach {
                    it.manga.favorite = false
                    it.manga.date_added = 0
                }
                if (!accepted.manga.favorite) {
                    accepted.manga.favorite = true
                    accepted.manga.date_added = System.currentTimeMillis()
                }

                val newAccepted = ChapterChain(accepted.manga, newChapters, history + urlHistory.map { it.second })
                val rootsToMutate = toDiscard + newAccepted

                db.inTransaction {
                    // Apply changes to all manga
                    db.insertMangas(rootsToMutate.map { it.manga }).executeAsBlocking()
                    // Insert new chapters for accepted manga
                    val chapterPutResults = db.insertChapters(newAccepted.chapters).executeAsBlocking().results()

                    // Delete the duplicate history first
                    if (deleteHistory.isNotEmpty()) {
                        db.deleteHistoryIds(deleteHistory).executeAsBlocking()
                    }
                    // Get a updated history list
                    val newHistory = urlHistory.mapNotNull { (url, history) ->
                        val result = chapterPutResults.firstNotNullOfOrNull { (chapter, result) ->
                            if (chapter.url == url) {
                                result.insertedId()
                            } else null
                        }
                        if (result != null) {
                            history.chapter_id = result
                            history
                        } else null
                    } + history
                    // Copy the new history chapter ids
                    db.updateHistoryChapterIds(newHistory).executeAsBlocking()

                    // Copy categories from all chains to accepted manga
                    val newCategories = rootsToMutate.flatMap {
                        db.getCategoriesForManga(it.manga).executeAsBlocking()
                    }.distinctBy { it.id }.map {
                        MangaCategory.create(newAccepted.manga, it)
                    }
                    db.setMangaCategories(newCategories, rootsToMutate.map { it.manga })
                }

                Triple(newAccepted, toDiscard, new)
            } else {
                /*val notNeeded = chains.filter { it.manga.id != accepted.manga.id }
                val (newChapters, new) = getChapterList(accepted, notNeeded, chainsAsChapters)
                val newAccepted = ChapterChain(accepted.manga, newChapters)

                // Insert new chapters for accepted manga
                db.insertChapters(newAccepted.chapters).await()*/

                Triple(accepted, emptyList(), false)
            }
        }
    }

    data class HistoryUpdates(
        val history: List<History>,
        val urlHistory: List<Pair<String, History>>,
        val historyToDelete: List<Long>,
    )

    private fun getHistory(
        newChapters: List<Chapter>,
        chainsAsChapters: List<Chapter>,
        chainsAsHistory: List<History>,
    ): HistoryUpdates {
        val historyMap = chainsAsHistory
            .groupBy { history ->
                chainsAsChapters.find { it.id == history.chapter_id }?.url.orEmpty()
            }
            .filterKeys { it.isNotBlank() }
        val latestHistory = historyMap.mapValues { entry ->
            entry.value.maxByOrNull {
                it.time_read
            }!!
        }
        val oldHistory = historyMap.flatMap { entry ->
            val topEntry = entry.value.maxByOrNull {
                it.time_read
            }!!
            entry.value - topEntry
        }.mapNotNull { it.id }
        return HistoryUpdates(
            latestHistory.filter { (_, history) ->
                val oldChapter = chainsAsChapters.find { it.id == history.chapter_id }
                val newChapter = newChapters.find { it.url == oldChapter?.url }
                if (oldChapter != newChapter && newChapter?.id != null) {
                    history.chapter_id = newChapter.id!!
                    true
                } else false
            }.mapNotNull { it.value },
            latestHistory.mapNotNull { (url, history) ->
                val oldChapter = chainsAsChapters.find { it.id == history.chapter_id }
                val newChapter = newChapters.find { it.url == oldChapter?.url }
                if (oldChapter != newChapter && newChapter?.id == null) {
                    url to history
                } else {
                    null
                }
            },
            oldHistory,
        )
    }

    private fun getChapterList(
        accepted: ChapterChain,
        toDiscard: List<ChapterChain>,
        chainsAsChapters: List<Chapter>,
    ): Pair<List<Chapter>, Boolean> {
        var new = false
        return toDiscard
            .flatMap { chain ->
                chain.chapters
            }
            .fold(accepted.chapters) { curChapters, chapter ->
                val existing = curChapters.find { it.url == chapter.url }

                val newLastPageRead = chainsAsChapters.maxOfOrNull { it.last_page_read }

                if (existing != null) {
                    existing.read = existing.read || chapter.read
                    existing.last_page_read = existing.last_page_read.coerceAtLeast(chapter.last_page_read)
                    if (newLastPageRead != null && existing.last_page_read <= 0) {
                        existing.last_page_read = newLastPageRead
                    }
                    existing.bookmark = existing.bookmark || chapter.bookmark
                    curChapters
                } else {
                    new = true
                    curChapters + Chapter.create().apply {
                        manga_id = accepted.manga.id
                        url = chapter.url
                        name = chapter.name
                        read = chapter.read
                        bookmark = chapter.bookmark

                        last_page_read = chapter.last_page_read
                        if (newLastPageRead != null && last_page_read <= 0) {
                            last_page_read = newLastPageRead
                        }

                        date_fetch = chapter.date_fetch
                        date_upload = chapter.date_upload
                    }
                }
            }
            .sortedBy { it.date_upload }
            .let { chapters ->
                chapters.onEachIndexed { index, chapter ->
                    chapter.name = "v${index + 1}: " + chapter.name.substringAfter(" ")
                    chapter.chapter_number = index + 1f
                    chapter.source_order = chapters.lastIndex - index
                }
            } to new
    }
}

data class GalleryEntry(val gId: String, val gToken: String) {
    class Serializer : MemAutoFlushingLookupTable.EntrySerializer<GalleryEntry> {
        /**
         * Serialize an entry as a String.
         */
        override fun write(entry: GalleryEntry) = with(entry) { "$gId:$gToken" }

        /**
         * Read an entry from a String.
         */
        override fun read(string: String): GalleryEntry {
            val colonIndex = string.indexOf(':')
            return GalleryEntry(
                string.substring(0, colonIndex),
                string.substring(colonIndex + 1, string.length),
            )
        }
    }
}
