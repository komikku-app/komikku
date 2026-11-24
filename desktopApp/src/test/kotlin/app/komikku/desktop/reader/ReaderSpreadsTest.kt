package app.komikku.desktop.reader

import app.komikku.desktop.ui.reader.ReaderModel
import app.komikku.desktop.ui.reader.pageKey
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

/**
 * P11 review fixes: one spread function for the paged viewer ([ReaderModel.State.spreads]): pairs from the chapter's
 * first page and again after every page shown alone, never across chapters, the same both ways; the preloader takes the
 * neighbouring spreads at their slots' widths and never a page that is shown.
 */
class ReaderSpreadsTest {

    private fun chapter(id: Long, pages: Int): ReaderChapter {
        val chapter = ReaderChapter(Chapter.create().copy(id = id, mangaId = 1, url = "/chapter/$id", name = "Chapter $id"))
        chapter.state = ReaderChapter.State.Loaded((0 until pages).map { i -> ReaderPage(i).also { it.chapter = chapter } })
        return chapter
    }

    private fun ReaderChapter.page(number: Int) = pages!![number - 1]

    private fun ReaderModel.State.spreadPages(): List<List<String>> = spreads.map { range -> range.map { describe(items[it]) } }

    private fun describe(item: ReaderItem): String = when (item) {
        is InsertPage -> "${item.chapter.chapter.id}.${item.number}b"
        is ReaderPage -> "${item.chapter.chapter.id}.${item.number}"
        is ChapterTransition.Prev -> "P${item.from.chapter.id}"
        is ChapterTransition.Next -> "N${item.from.chapter.id}"
    }

    @Test
    fun `double pages pair from the chapter start and after a wide page, the same backwards`() {
        val one = chapter(1, 5)
        val items = listOf(ChapterTransition.Prev(one, null)) + one.pages!! + ChapterTransition.Next(one, null)
        val plain = ReaderModel.State(items = items, doublePages = true)
        plain.spreadPages() shouldBe listOf(listOf("P1"), listOf("1.1", "1.2"), listOf("1.3", "1.4"), listOf("1.5"), listOf("N1"))

        val wide = plain.copy(widePages = setOf(pageKey(one.page(3))))
        wide.spreadPages() shouldBe listOf(listOf("P1"), listOf("1.1", "1.2"), listOf("1.3"), listOf("1.4", "1.5"), listOf("N1"))
        // Every item belongs to exactly one spread, so a turn back from a spread lands on the spread before it
        wide.spreadIndexOf(items.indexOf(one.page(5))) shouldBe 3
        wide.spreadIndexOf(items.indexOf(one.page(4))) shouldBe 3

        plain.copy(doublePages = false).spreads.all { it.first == it.last } shouldBe true
    }

    @Test
    fun `spreads never pair pages of two chapters or a split half`() {
        val one = chapter(1, 3)
        val two = chapter(2, 2)
        val state = ReaderModel.State(items = one.pages!! + two.pages!!, doublePages = true)
        state.spreadPages() shouldBe listOf(listOf("1.1", "1.2"), listOf("1.3"), listOf("2.1", "2.2"))

        val half = InsertPage(one.page(1))
        val split = ReaderModel.State(
            items = listOf(one.page(1), half, one.page(2), one.page(3)),
            doublePages = true,
            widePages = setOf(pageKey(one.page(1))),
            splitPages = setOf(pageKey(one.page(1))),
        )
        split.spreadPages() shouldBe listOf(listOf("1.1"), listOf("1.1b"), listOf("1.2", "1.3"))
    }

    @Test
    fun `the preloader takes the neighbouring spreads at their widths, never a shown page`() {
        val one = chapter(1, 6)
        val items = one.pages!! + ChapterTransition.Next(one, null)
        val wide = setOf(pageKey(one.page(3)))
        // Shown: 1 + 2; ahead: 3 alone (twice the window width), then 4 + 5 (one window width each)
        val state = ReaderModel.State(items = items, doublePages = true, widePages = wide)
        state.shownPages.map(::describe) shouldBe listOf("1.1", "1.2")
        state.preloadPages(1000).map { describe(it.first) to it.second } shouldBe
            listOf("1.3" to 2000, "1.4" to 1000, "1.5" to 1000)

        // On the second half of a split page: its page is shown, so it is not decoded again at another width
        val half = InsertPage(one.page(3))
        val splitItems = listOf(one.page(1), one.page(2), one.page(3), half, one.page(4))
        val onHalf = ReaderModel.State(items = splitItems, currentIndex = 3, widePages = wide, splitPages = wide)
        onHalf.shownPages.map(::describe) shouldBe listOf("1.3b")
        onHalf.preloadPages(1000).map { describe(it.first) } shouldBe listOf("1.4")
    }
}
