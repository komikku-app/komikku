package exh.eh

import android.content.Context
import eu.kanade.domain.manga.interactor.UpdateManga
import exh.metadata.metadata.EHentaiSearchMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChapterByUrl
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistoryByMangaId
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.InsertFavoriteEntryAlternative
import tachiyomi.domain.manga.model.FavoriteEntryAlternative
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
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
    private val getChaptersByMangaId: GetChaptersByMangaId by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val chapterRepository: ChapterRepository by injectLazy()
    private val upsertHistory: UpsertHistory by injectLazy()
    private val removeHistory: RemoveHistory by injectLazy()
    private val getHistoryByMangaId: GetHistoryByMangaId by injectLazy()
    private val insertFavoriteEntryAlternative: InsertFavoriteEntryAlternative by injectLazy()

    /**
     * @param chapters Cannot be an empty list!
     *
     * @return Triple<Accepted, Discarded, HasNew>
     */
    suspend fun findAcceptedRootAndDiscardOthers(
        sourceId: Long,
        chapters: List<Chapter>,
    ): Triple<ChapterChain, List<ChapterChain>, List<Chapter>> {
        // Find other chains
        val chains = chapters
            .flatMap { chapter ->
                getChapterByUrl.await(chapter.url).map { it.mangaId }
            }
            .distinct()
            .mapNotNull { mangaId ->
                coroutineScope {
                    val manga = async(Dispatchers.IO) {
                        getManga.await(mangaId)
                    }
                    val chapterList = async(Dispatchers.IO) {
                        getChaptersByMangaId.await(mangaId)
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

        // Accept oldest chain
        val accepted = chains.minBy { it.manga.id }

        val toDiscard = chains.filter { it.manga.favorite && it.manga.id != accepted.manga.id }
        val mangaUpdates = mutableListOf<MangaUpdate>()

        val chainsAsChapters = chains.flatMap { it.chapters }
        val chainsAsHistory = chains.flatMap { it.history }

        return if (toDiscard.isNotEmpty()) {
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

            val (newHistory, deleteHistory) = getHistory(
                getChaptersByMangaId.await(accepted.manga.id),
                chainsAsChapters,
                chainsAsHistory,
            )

            // Delete the duplicate history first
            deleteHistory.forEach {
                removeHistory.awaitById(it)
            }

            // Insert new history
            newHistory.forEach {
                upsertHistory.await(it)
            }

            // Update favorites entry database
            val favoriteEntryUpdate = getFavoriteEntryAlternative(accepted, toDiscard)
            if (favoriteEntryUpdate != null) {
                insertFavoriteEntryAlternative.await(favoriteEntryUpdate)
            }

            // Copy categories from all chains to accepted manga

            val newCategories = rootsToMutate.flatMap { chapterChain ->
                getCategories.await(chapterChain.manga.id).map { it.id }
            }.distinct()
            rootsToMutate.forEach {
                setMangaCategories.await(it.manga.id, newCategories)
            }

            Triple(newAccepted, toDiscard, newChapters)
        } else {
            /*val notNeeded = chains.filter { it.manga.id != accepted.manga.id }
            val (newChapters, new) = getChapterList(accepted, notNeeded, chainsAsChapters)
            val newAccepted = ChapterChain(accepted.manga, newChapters)

            // Insert new chapters for accepted manga
            db.insertChapters(newAccepted.chapters).await()*/

            Triple(accepted, emptyList(), emptyList())
        }
    }

    private fun getFavoriteEntryAlternative(
        accepted: ChapterChain,
        toDiscard: List<ChapterChain>,
    ): FavoriteEntryAlternative? {
        val favorite = toDiscard.find { it.manga.favorite } ?: return null

        val gid = EHentaiSearchMetadata.galleryId(accepted.manga.url)
        val token = EHentaiSearchMetadata.galleryToken(accepted.manga.url)

        return FavoriteEntryAlternative(
            otherGid = gid,
            otherToken = token,
            gid = EHentaiSearchMetadata.galleryId(favorite.manga.url),
            token = EHentaiSearchMetadata.galleryToken(favorite.manga.url),
        )
    }

    private fun getHistory(
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
            } else {
                null
            }
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
                        } else {
                            it
                        }
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
                        } else {
                            chapter.lastPageRead
                        },
                        dateFetch = chapter.dateFetch,
                        dateUpload = chapter.dateUpload,
                        chapterNumber = -1.0,
                        scanlator = null,
                        sourceOrder = -1,
                        lastModifiedAt = 0,
                        version = 0,
                    )
                }
            }
            .sortedBy { it.dateUpload }
            .let { chapters ->
                val updates = mutableListOf<ChapterUpdate>()
                val newChapters = mutableListOf<Chapter>()
                chapters.mapIndexed { index, chapter ->
                    val name = "v${index + 1}: " + chapter.name.substringAfter(" ")
                    val chapterNumber = index + 1.0
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
