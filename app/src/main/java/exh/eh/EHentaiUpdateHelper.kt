package exh.eh

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.util.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Date

data class ChapterChain(val manga: Manga, val chapters: List<Chapter>)

class EHentaiUpdateHelper(context: Context) {
    val parentLookupTable =
        MemAutoFlushingLookupTable(
            File(context.filesDir, "exh-plt.maftable"),
            GalleryEntry.Serializer()
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
                    db.getChapters(chapter.url).await().mapNotNull { it.manga_id }
                }.distinct()
            }
            .map { mangaIds ->
                mangaIds
                    .mapNotNull { mangaId ->
                        (db.getManga(mangaId).await() ?: return@mapNotNull null) to db.getChapters(mangaId).await()
                    }
                    .map {
                        ChapterChain(it.first, it.second)
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

            if (toDiscard.isNotEmpty()) {
                var new = false

                // Copy chain chapters to curChapters
                val newChapters = toDiscard
                    .flatMap { chain ->
                        chain.chapters
                    }
                    .fold(accepted.chapters) { curChapters, chapter ->
                        val existing = curChapters.find { it.url == chapter.url }

                        val newLastPageRead = chainsAsChapters.maxByOrNull { it.last_page_read }?.last_page_read

                        if (existing != null) {
                            existing.read = existing.read || chapter.read
                            existing.last_page_read = existing.last_page_read.coerceAtLeast(chapter.last_page_read)
                            if (newLastPageRead != null && existing.last_page_read <= 0) {
                                existing.last_page_read = newLastPageRead
                            }
                            existing.bookmark = existing.bookmark || chapter.bookmark
                            curChapters
                        }
                        else {
                            new = true
                            curChapters + ChapterImpl().apply {
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
                    .apply {
                        mapIndexed { index, chapter ->
                            chapter.name = "v${index + 1}: " + chapter.name.substringAfter(" ")
                            chapter.chapter_number = index + 1f
                            chapter.source_order = lastIndex - index
                        }
                    }

                toDiscard.forEach {
                    it.manga.favorite = false
                    it.manga.date_added = 0
                }
                accepted.manga.favorite = true
                accepted.manga.date_added = Date().time

                val newAccepted = ChapterChain(accepted.manga, newChapters)
                val rootsToMutate = toDiscard + newAccepted

                db.inTransaction {
                    // Apply changes to all manga
                    db.insertMangas(rootsToMutate.map { it.manga }).executeAsBlocking()
                    // Insert new chapters for accepted manga
                    db.insertChapters(newAccepted.chapters).executeAsBlocking()
                    // Copy categories from all chains to accepted manga
                    val newCategories = rootsToMutate.flatMap {
                        db.getCategoriesForManga(it.manga).executeAsBlocking()
                    }.distinctBy { it.id }.map {
                        MangaCategory.create(newAccepted.manga, it)
                    }
                    db.setMangaCategories(newCategories, rootsToMutate.map { it.manga })
                }

                Triple(newAccepted, toDiscard, new)
            } else Triple(accepted, emptyList(), false)
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
                string.substring(colonIndex + 1, string.length)
            )
        }
    }
}
