package app.komikku.shared.chapter

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
}
