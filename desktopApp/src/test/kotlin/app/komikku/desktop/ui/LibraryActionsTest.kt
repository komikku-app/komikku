package app.komikku.desktop.ui

import eu.kanade.tachiyomi.ui.library.toggleLibraryRangeSelection
import eu.kanade.tachiyomi.ui.library.toggleLibrarySelection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The shared selection logic of `LibraryActions.kt` (Android and desktop). */
class LibraryActionsTest {

    @Test
    fun `a range selects between the last selected entry and the clicked one`() {
        val page = listOf(1L, 2, 3, 4, 5)
        setOf(2L).toggleLibraryRangeSelection(mangaId = 4, categoryItems = page, isSameCategory = true) shouldBe setOf(2L, 3, 4)
        setOf(4L).toggleLibraryRangeSelection(mangaId = 2, categoryItems = page, isSameCategory = true) shouldBe setOf(4L, 2, 3)
        setOf(9L).toggleLibraryRangeSelection(mangaId = 2, categoryItems = page, isSameCategory = false) shouldBe setOf(9L, 2)
    }

    @Test
    fun `a range whose anchor is not on the page selects only the clicked entry`() {
        // Select A on page 1; on page 2 select then deselect B (the page stays the last selection's page); range-select
        // C on page 2: the anchor (A) is not on page 2.
        val page2 = listOf(10L, 11, 12)
        val selection = setOf(1L).toggleLibrarySelection(11).toggleLibrarySelection(11)
        selection.toggleLibraryRangeSelection(mangaId = 12, categoryItems = page2, isSameCategory = true) shouldBe setOf(1L, 12)
        // The clicked entry is not on the page (a stale page): no crash either
        setOf(10L).toggleLibraryRangeSelection(mangaId = 99, categoryItems = page2, isSameCategory = true) shouldBe setOf(10L, 99)
    }
}
