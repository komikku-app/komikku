package exh.eh

import android.content.Context
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.GetChapterByUrl
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.history.interactor.GetHistoryByMangaId
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.UpsertHistory
import eu.kanade.domain.history.model.History
import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
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
    private val getChapterByUrl: GetChapterByUrl by injectLazy()
    private val getChapterByMangaId: GetChapterByMangaId by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val chapterRepository: ChapterRepository by injectLazy()
    private val upsertHistory: UpsertHistory by injectLazy()
    private val removeHistoryById: RemoveHistoryById by injectLazy()
    private val getHistoryByMangaId: GetHistoryByMangaId by injectLazy()

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
                    getChapterByUrl.await(chapter.url).map { it.mangaId }
                }.distinct()
            }
            .map { mangaIds ->
                mangaIds
                    .mapNotNull { mangaId ->
                        coroutineScope {
                            val manga = async(Dispatchers.IO) {
                                getManga.await(mangaId)
                            }
                            val chapterList = async(Dispatchers.IO) {
                                getChapterByMangaId.await(mangaId)
                            }
                            val history = async(Dispatchers.IO) {
                                getHistoryByMangaId.await(mangaId)
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
            val acceptedChain = chains.minBy { it.manga.id }

            acceptedChain to chains
        }

        return chainsWithAccepted.map { (accepted, chains) ->
            val toDiscard = chains.filter { it.manga.favorite && it.manga.id != accepted.manga.id }
            val mangaUpdates = mutableListOf<MangaUpdate>()

            val chainsAsChapters = chains.flatMap { it.chapters }
            val chainsAsHistory = chains.flatMap { it.history }

            if (toDiscard.isNotEmpty()) {
                // Copy chain chapters to curChapters
                val (chapterUpdates, newChapters, new) = getChapterList(accepted, toDiscard, chainsAsChapters)

                toDiscard.forEach {
                    mangaUpdates += MangaUpdate(
                        id = it.manga.id,
                        favorite = false,
                        dateAdded = 0,
                    )
                }
                if (!accepted.manga.favorite) {
                    mangaUpdates += MangaUpdate(
                        id = accepted.manga.id,
                        favorite = true,
                        dateAdded = System.currentTimeMillis(),
                    )
                }

                val newAccepted = ChapterChain(accepted.manga, newChapters, emptyList())
                val rootsToMutate = toDiscard + newAccepted

                // Apply changes to all manga
                updateManga.awaitAll(mangaUpdates)
                // Insert new chapters for accepted manga
                chapterRepository.updateAll(chapterUpdates)
                chapterRepository.addAll(newChapters)

                val (newHistory, deleteHistory) = getHistory(getChapterByMangaId.await(accepted.manga.id), chainsAsChapters, chainsAsHistory)

                // Delete the duplicate history first
                deleteHistory.forEach {
                    removeHistoryById.await(it)
                }

                // Insert new history
                newHistory.forEach {
                    upsertHistory.await(it)
                }

                // Copy categories from all chains to accepted manga

                val newCategories = rootsToMutate.flatMap { chapterChain ->
                    getCategories.await(chapterChain.manga.id).map { it.id }
                }.distinct()
                rootsToMutate.forEach {
                    setMangaCategories.await(it.manga.id, newCategories)
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

    fun getHistory(
        currentChapters: List<Chapter>,
        chainsAsChapters: List<Chapter>,
        chainsAsHistory: List<History>,
    ): Pair<List<HistoryUpdate>, List<Long>> {
        val history = chainsAsHistory.groupBy { history -> chainsAsChapters.find { it.id == history.chapterId }?.url }
        val newHistory = currentChapters.mapNotNull { chapter ->
            val newHistory = history[chapter.url]
                ?.maxByOrNull {
                    it.readAt?.time ?: 0
                }
                ?.takeIf { it.chapterId != chapter.id && it.readAt != null }
            if (newHistory != null) {
                HistoryUpdate(chapter.id, newHistory.readAt!!, newHistory.readDuration)
            } else null
        }
        val currentChapterIds = currentChapters.map { it.id }
        val historyToDelete = chainsAsHistory.filterNot { it.chapterId in currentChapterIds }
            .map { it.id }
        return newHistory to historyToDelete
    }

    private fun getChapterList(
        accepted: ChapterChain,
        toDiscard: List<ChapterChain>,
        chainsAsChapters: List<Chapter>,
    ): Triple<List<ChapterUpdate>, List<Chapter>, Boolean> {
        var new = false
        return toDiscard
            .flatMap { chain ->
                chain.chapters
            }
            .fold(accepted.chapters) { curChapters, chapter ->
                val newLastPageRead = chainsAsChapters.maxOfOrNull { it.lastPageRead }

                if (curChapters.any { it.url == chapter.url }) {
                    curChapters.map {
                        if (it.url == chapter.url) {
                            val read = it.read || chapter.read
                            var lastPageRead = it.lastPageRead.coerceAtLeast(chapter.lastPageRead)
                            if (newLastPageRead != null && lastPageRead <= 0) {
                                lastPageRead = newLastPageRead
                            }
                            val bookmark = it.bookmark || chapter.bookmark
                            it.copy(
                                read = read,
                                lastPageRead = lastPageRead,
                                bookmark = bookmark,
                            )
                        } else it
                    }
                } else {
                    new = true
                    curChapters + Chapter(
                        id = -1,
                        mangaId = accepted.manga.id,
                        url = chapter.url,
                        name = chapter.name,
                        read = chapter.read,
                        bookmark = chapter.bookmark,
                        lastPageRead = if (newLastPageRead != null && chapter.lastPageRead <= 0) {
                            newLastPageRead
                        } else chapter.lastPageRead,
                        dateFetch = chapter.dateFetch,
                        dateUpload = chapter.dateUpload,
                        chapterNumber = -1F,
                        scanlator = null,
                        sourceOrder = -1,
                    )
                }
            }
            .sortedBy { it.dateUpload }
            .let { chapters ->
                val updates = mutableListOf<ChapterUpdate>()
                val newChapters = mutableListOf<Chapter>()
                chapters.mapIndexed { index, chapter ->
                    val name = "v${index + 1}: " + chapter.name.substringAfter(" ")
                    val chapterNumber = index + 1f
                    val sourceOrder = chapters.lastIndex - index.toLong()
                    when (chapter.id) {
                        -1L -> newChapters.add(
                            chapter.copy(
                                name = name,
                                chapterNumber = chapterNumber,
                                sourceOrder = sourceOrder,
                            ),
                        )
                        else -> updates.add(
                            ChapterUpdate(
                                id = chapter.id,
                                name = name.takeUnless { chapter.name == it },
                                chapterNumber = chapterNumber.takeUnless { chapter.chapterNumber == it },
                                sourceOrder = sourceOrder.takeUnless { chapter.sourceOrder == it },
                            ),
                        )
                    }
                }
                Triple(updates.toList(), newChapters.toList(), new)
            }
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
