package app.komikku.shared.reader

import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga

/**
 * KMK: the reader's chapter list rules, extracted from Android's `ReaderViewModel.chapterList` (same
 * filters and order) so platform readers share them: skip read / skip filtered (the selected chapter
 * always stays), reading order = the manga's sort mode ascending, optional duplicate removal around
 * the selected chapter, optional "downloaded only".
 */
object ReaderChapterList {

    data class Options(
        val skipRead: Boolean = false,
        val skipFiltered: Boolean = true,
        val skipDupe: Boolean = false,
        val downloadedOnly: Boolean = false,
    )

    /**
     * @param chapters the manga's chapters with the manga's filters applied (Android:
     * `getChaptersByMangaId.await(manga.id, applyFilter = true)`).
     * @param isDownloaded whether a chapter is available offline; local manga count as downloaded.
     * @throws IllegalStateException if [selectedChapterId] is not in [chapters] (same as Android).
     */
    fun build(
        manga: Manga,
        chapters: List<Chapter>,
        selectedChapterId: Long,
        options: Options,
        isDownloaded: (Chapter) -> Boolean = { false },
    ): List<Chapter> {
        val selectedChapter = chapters.find { it.id == selectedChapterId }
            ?: error("Requested chapter of id $selectedChapterId not found in chapter list")

        val chaptersForReader = if (options.skipRead || options.skipFiltered) {
            val filteredChapters = chapters.filterNot {
                when {
                    options.skipRead && it.read -> true
                    options.skipFiltered -> {
                        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                            (manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED && !isDownloaded(it)) ||
                            (manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED && isDownloaded(it)) ||
                            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                    }
                    else -> false
                }
            }
            if (filteredChapters.any { it.id == selectedChapterId }) {
                filteredChapters
            } else {
                filteredChapters + listOf(selectedChapter)
            }
        } else {
            chapters
        }

        return chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .let { if (options.skipDupe) it.removeDuplicates(selectedChapter) else it }
            .let { list -> if (options.downloadedOnly) list.filter { it.id == selectedChapterId || isDownloaded(it) } else list }
    }

    /** The neighbours of [chapterId] in a list from [build] (`null` at either end). */
    fun neighbours(list: List<Chapter>, chapterId: Long): Pair<Chapter?, Chapter?> {
        val index = list.indexOfFirst { it.id == chapterId }
        if (index == -1) return null to null
        return list.getOrNull(index - 1) to list.getOrNull(index + 1)
    }
}
