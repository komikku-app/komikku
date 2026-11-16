package app.komikku.shared.chapter

import eu.kanade.presentation.manga.DownloadAction
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga

/**
 * KMK: platform-neutral chapter-list rules of Android's `MangaScreenModel`, written against domain
 * types so both apps can use them (desktop now, Android when the manga screen is shared).
 */
object ChapterListActions {

    /** Chapters in the order the manga's sort flags show them (Android: `getChapterSort(manga)`). */
    fun sortForDisplay(manga: Manga, chapters: List<Chapter>): List<Chapter> =
        chapters.sortedWith(getChapterSort(manga))

    /**
     * The chapters "before" [pointer] in reading order, from the displayed (filtered, sorted) list,
     * as `MangaScreenModel.markPreviousChapterRead` computes them.
     */
    fun previousChapters(manga: Manga, displayed: List<Chapter>, pointer: Chapter): List<Chapter> {
        val prevChapters = if (manga.sortDescending()) displayed.asReversed() else displayed
        val pointerPos = prevChapters.indexOfFirst { it.id == pointer.id }
        return if (pointerPos == -1) emptyList() else prevChapters.take(pointerPos)
    }

    /**
     * The chapters a "download …" menu action queues (Android `MangaScreenModel.runDownloadAction`):
     * unread (or bookmarked) chapters that are not downloaded or queued, from [chapters] (Android: the
     * filtered list when "skip filtered chapters" is on, else all chapters); the "next N" actions take
     * them in reading order.
     */
    fun chaptersToDownload(
        manga: Manga,
        chapters: List<Chapter>,
        action: DownloadAction,
        isNotDownloaded: (Chapter) -> Boolean,
    ): List<Chapter> {
        val unread = chapters.filter { !it.read && isNotDownloaded(it) }
        fun unreadSorted(): List<Chapter> {
            val sorted = unread.sortedWith(getChapterSort(manga)).let { if (manga.isEhBasedManga()) it.reversed() else it }
            return if (manga.sortDescending()) sorted.reversed() else sorted
        }
        return when (action) {
            DownloadAction.NEXT_1_CHAPTER -> unreadSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> unreadSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> unreadSorted().take(10)
            DownloadAction.NEXT_25_CHAPTERS -> unreadSorted().take(25)
            DownloadAction.UNREAD_CHAPTERS -> unread
            DownloadAction.BOOKMARKED_CHAPTERS -> chapters.filter { it.bookmark && isNotDownloaded(it) }
        }
    }
}
