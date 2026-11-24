package app.komikku.shared.reader

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap

/**
 * KMK: the items a reader viewer shows for [ViewerChapters], the rule of Android's `PagerViewerAdapter.setChapters` and
 * `WebtoonAdapter.setChapters` (both the same, without the pager's split / insert pages): the previous chapter's pages
 * when loaded, the "previous" transition, the current chapter's pages, the "next" transition, the next chapter's pages
 * when loaded ([ReaderChapter.pages] is only set then). A transition is left out only when its neighbour is loaded, no chapters are missing in between and no
 * transition is forced (`alwaysShowChapterTransition`, or the viewer is on a transition, so it does not disappear
 * under the user when the neighbour finishes loading).
 *
 * Android's adapters keep their own copies (Android view code with `DiffUtil` / insert pages); this is the desktop's.
 */
object ReaderViewerItems {

    fun build(chapters: ViewerChapters, forceTransition: Boolean): List<ReaderItem> = buildList {
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        chapters.prevChapter?.pages?.let(::addAll)
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        chapters.currChapter.pages?.let(::addAll)

        if (nextHasMissingChapters || forceTransition || chapters.nextChapter?.state !is ReaderChapter.State.Loaded) {
            add(ChapterTransition.Next(chapters.currChapter, chapters.nextChapter))
        }
        chapters.nextChapter?.pages?.let(::addAll)
    }

    /**
     * The index of [item] in [items], for keeping the viewer on the same item when the list is rebuilt: pages by
     * identity, transitions by Android's `ChapterTransition.equals` (the same pair of chapters in either direction).
     */
    fun indexOf(items: List<ReaderItem>, item: ReaderItem?): Int = when (item) {
        is ReaderPage -> items.indexOfFirst { it === item }
        is ChapterTransition -> items.indexOfFirst { it is ChapterTransition && it == item }
        null -> -1
    }

    /**
     * The spreads of [items] (P11 review fixes): with [doublePages] a page and the next item are shown together when
     * both are [pairable] pages of the same chapter, paired from the chapter's first page and again after every page
     * shown alone (SY's double pages: a wide page or a split half is shown alone); else every item alone. Transitions
     * are always alone. Forward and backward page turns, progress and preloading all go by these, so going back shows
     * the same spreads as going forward.
     */
    fun spreads(items: List<ReaderItem>, doublePages: Boolean, pairable: (ReaderPage) -> Boolean): List<IntRange> {
        val spreads = ArrayList<IntRange>(items.size)
        var i = 0
        while (i < items.size) {
            val page = items[i] as? ReaderPage
            val next = items.getOrNull(i + 1) as? ReaderPage
            val pair = doublePages && page != null && next != null && next.chapter == page.chapter && pairable(page) && pairable(next)
            spreads += if (pair) i..i + 1 else i..i
            i += if (pair) 2 else 1
        }
        return spreads
    }

    /** The position in [spreads] (in order, covering every item) of the spread holding [index]; `-1` for none. */
    fun spreadIndexOf(spreads: List<IntRange>, index: Int): Int {
        if (spreads.isEmpty() || index < 0 || index > spreads.last().last) return -1
        var low = 0
        var high = spreads.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val spread = spreads[mid]
            when {
                index < spread.first -> high = mid - 1
                index > spread.last -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }
}
