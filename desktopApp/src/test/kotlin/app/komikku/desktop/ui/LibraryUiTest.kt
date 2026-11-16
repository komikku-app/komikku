package app.komikku.desktop.ui

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.window.DesktopShortcuts
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless library UI test: search field (`library-search`, debounced, filtered in the screen model),
 * category tab counts (`categoryNumberOfItems`), unread badges (`unread-badge-<id>`: hidden at 0, follow
 * `unreadBadge`) and the "no results" empty state.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryUiTest {

    private lateinit var app: Application
    private lateinit var blue: Manga
    private lateinit var frieren: Manga
    private lateinit var meshi: Manga

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        runBlocking {
            val categories = Injekt.get<CategoryRepository>()
            val reading = categories.insert(Category(id = 0, name = "Reading", order = 1, flags = 0, hidden = false))
            val later = categories.insert(Category(id = 0, name = "Later", order = 2, flags = 0, hidden = false))
            blue = TestLibrary.insertManga(SOURCE_ID, "/blue", "Blue Period")
            frieren = TestLibrary.insertManga(SOURCE_ID, "/frieren", "Frieren")
            meshi = TestLibrary.insertManga(SOURCE_ID, "/meshi", "Dungeon Meshi")
            TestLibrary.insertChapters(blue, "/blue/2" to "Chapter 2", "/blue/1" to "Chapter 1")
            TestLibrary.insertChapters(meshi, "/meshi/1" to "Chapter 1")
            val mangaRepository = Injekt.get<MangaRepository>()
            mangaRepository.setMangaCategories(blue.id, listOf(reading))
            mangaRepository.setMangaCategories(frieren.id, listOf(reading))
            mangaRepository.setMangaCategories(meshi.id, listOf(later))
        }
    }

    @Test
    fun `search, tab counts and unread badges`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        preferences.unreadBadge().set(true)
        preferences.categoryNumberOfItems().set(false)
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Blue Period")

        // Unread badge: count shown, hidden for a manga without unread chapters
        onNodeWithTag("unread-badge-${blue.id}", useUnmergedTree = true).assertTextEquals("2")
        onAllNodesWithTag("unread-badge-${frieren.id}", useUnmergedTree = true).assertCountEquals(0)

        // Tab counts follow the preference
        onAllNodesWithText("Reading (2)").assertCountEquals(0)
        runOnIdle { preferences.categoryNumberOfItems().set(true) }
        waitForText("Reading (2)")
        waitForText("Later (1)")

        // Search: debounced, trimmed, Android matcher (partial title); other categories hidden while searching
        onNodeWithTag("library-search").performTextInput("  meshi ")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Blue Period").fetchSemanticsNodes().isEmpty() }
        waitForText("Dungeon Meshi")

        // No match: Android's "No results found", not the empty-category message
        onNodeWithTag("library-search").performTextClearance()
        onNodeWithTag("library-search").performTextInput("zzz")
        waitForText(MR.strings.no_results_found.text())
        onAllNodesWithText(MR.strings.information_no_manga_category.text()).assertCountEquals(0)

        // Global "downloaded only" with nothing downloaded: "no match" with the search field, not the
        // empty-library screen (the library itself is not empty)
        onNodeWithTag("library-search").performTextClearance()
        runOnIdle { Injekt.get<BasePreferences>().downloadedOnly().set(true) }
        waitForText(MR.strings.error_no_match.text())
        onAllNodesWithText(MR.strings.information_empty_library.text()).assertCountEquals(0)
        onNodeWithTag("library-search").assertExists()
        runOnIdle { Injekt.get<BasePreferences>().downloadedOnly().set(false) }

        // Cleared search shows everything again; badges follow the unread badge preference
        onNodeWithTag("library-search").performTextClearance()
        waitForText("Blue Period")
        runOnIdle { preferences.unreadBadge().set(false) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("unread-badge-${blue.id}", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `settings dialog sorts, filters and switches the display mode`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        preferences.unreadBadge().set(true)
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Frieren")

        // Sort: total chapters (Android keeps the current direction for a new mode, a second click flips it),
        // written to the categories' flags + the global sort like Android
        onNodeWithTag("library-settings").performClick()
        onNodeWithTag("library-settings-tab-1").performClick()
        onNodeWithText(MR.strings.action_sort_total.text()).performClick()
        val first = waitForCategorySort(LibrarySort.Type.TotalChapters, direction = null)
        assertOrder(ascending = first.isAscending)
        onNodeWithText(MR.strings.action_sort_total.text()).performClick()
        val second = waitForCategorySort(LibrarySort.Type.TotalChapters, direction = !first.isAscending)
        assertOrder(ascending = second.isAscending)
        preferences.sortingMode().get() shouldBe second

        // Filter: unread only (tri-state, Android preference)
        onNodeWithTag("library-settings-tab-0").performClick()
        onNodeWithText(MR.strings.action_filter_unread.text()).performClick()
        preferences.filterUnread().get() shouldBe TriState.ENABLED_IS
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Frieren").fetchSemanticsNodes().isEmpty() }

        // Display: list mode
        onNodeWithTag("library-settings-tab-2").performClick()
        onNodeWithTag("display-mode-LIST").performClick()
        preferences.displayMode().get() shouldBe LibraryDisplayMode.List
        onNodeWithText(MR.strings.action_close.text()).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("library-list").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-item-${blue.id}").assertExists()

        runOnIdle { preferences.filterUnread().set(TriState.DISABLED) }
        waitForText("Frieren")
    }

    /** Waits until every category has [type] (and [direction] ascending / not, when given); returns the sort. */
    private fun ComposeUiTest.waitForCategorySort(type: LibrarySort.Type, direction: Boolean?): LibrarySort {
        var sort: LibrarySort? = null
        waitUntil(timeoutMillis = 5_000) {
            val sorts = runBlocking { Injekt.get<GetCategories>().await() }.map { LibrarySort.valueOf(it.flags) }.distinct()
            sort = sorts.singleOrNull()?.takeIf { it.type == type && (direction == null || it.isAscending == direction) }
            sort != null
        }
        return sort!!
    }

    /** Frieren (no chapters) before Blue Period (2 chapters) when ascending by total chapters. */
    private fun ComposeUiTest.assertOrder(ascending: Boolean) =
        waitUntil(timeoutMillis = 5_000) { (left(frieren) < left(blue)) == ascending }

    private fun ComposeUiTest.left(manga: Manga): Float =
        onNodeWithTag("library-item-${manga.id}").fetchSemanticsNode().boundsInRoot.left

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun StringResource.text(): String = localized()

    private companion object {
        const val SOURCE_ID = 4711L
    }
}
