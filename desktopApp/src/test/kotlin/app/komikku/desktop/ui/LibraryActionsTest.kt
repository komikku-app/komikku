package app.komikku.desktop.ui

import eu.kanade.tachiyomi.ui.library.LibraryLastSelection
import eu.kanade.tachiyomi.ui.library.lastLibrarySelectionAfterToggle
import eu.kanade.tachiyomi.ui.library.toggleLibraryRangeSelection
import eu.kanade.tachiyomi.ui.library.toggleLibrarySelection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The selection logic of the desktop copy `LibraryActions.kt`, pinned to `master`'s `LibraryScreenModel`
 * (`toggleSelection`, `toggleRangeSelection` and `LastSelection`, synced to 15610e67e4).
 */
class LibraryActionsTest {

    private val page = listOf(1L, 2, 3, 4, 5)

    @Test
    fun `a range selects between the anchor and the clicked entry, both included, in either direction`() {
        setOf(2L).toggleLibraryRangeSelection(1, mangaId = 4, page, LibraryLastSelection(1, 2)) shouldBe setOf(2L, 3, 4)
        setOf(4L).toggleLibraryRangeSelection(1, mangaId = 2, page, LibraryLastSelection(1, 4)) shouldBe setOf(4L, 2, 3)
    }

    @Test
    fun `a range click on the anchor itself keeps it selected`() {
        setOf(3L).toggleLibraryRangeSelection(1, mangaId = 3, page, LibraryLastSelection(1, 3)) shouldBe setOf(3L)
    }

    @Test
    fun `without an anchor in the same category a range click selects only the clicked entry`() {
        setOf(9L).toggleLibraryRangeSelection(1, mangaId = 2, page, LibraryLastSelection(7, 9)) shouldBe setOf(9L, 2)
        setOf(9L).toggleLibraryRangeSelection(1, mangaId = 2, page, null) shouldBe setOf(9L, 2)
        // The clicked entry is not on the page (a stale page): no crash
        setOf(1L).toggleLibraryRangeSelection(1, mangaId = 99, page, LibraryLastSelection(1, 1)) shouldBe setOf(1L, 99)
    }

    @Test
    fun `the anchor is the entry last clicked, not the last one in the selection`() {
        // Click 2, then 5 (anchor 5), then 5 again: 5 is deselected, the anchor is gone although 2 stays selected
        var selection = emptySet<Long>()
        var anchor: LibraryLastSelection? = null
        for (clicked in listOf(2L, 5L, 5L)) {
            selection = selection.toggleLibrarySelection(clicked)
            anchor = lastLibrarySelectionAfterToggle(1, clicked, selection)
        }
        selection shouldBe setOf(2L)
        anchor shouldBe null
        selection.toggleLibraryRangeSelection(1, mangaId = 4, page, anchor) shouldBe setOf(2L, 4)
    }

    @Test
    fun `an anchor that was deselected by other means does not start a range`() {
        // Anchor 2 recorded, but 2 is no longer in the selection (e.g. removed by an invert the caller did not report)
        setOf(1L).toggleLibraryRangeSelection(1, mangaId = 4, page, LibraryLastSelection(1, 2)) shouldBe setOf(1L, 4)
    }
}
