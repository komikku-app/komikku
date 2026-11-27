package app.komikku.desktop.ui

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.components.TAB_HORIZONTAL_PADDING
import app.komikku.desktop.ui.components.TAB_ROW_FIXED_TAG
import app.komikku.desktop.ui.components.TAB_ROW_SCROLLABLE_TAG
import app.komikku.desktop.ui.components.tabsFitEqually
import app.komikku.desktop.window.DesktopShortcuts
import app.komikku.desktop.window.DesktopWindowPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * M2 (first Mac test): in a narrow window the Browse tab titles and the Library search field wrapped onto more lines
 * and made their bars taller. At the window's minimum width they now keep the height they have in a wide window; the
 * Browse tabs scroll sideways instead.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NarrowWindowUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        runBlocking {
            val category = Injekt.get<CategoryRepository>()
                .insert(Category(id = 0, name = "A rather long category name", order = 1, flags = 0, hidden = false))
            val manga = TestLibrary.insertManga(SOURCE_ID, "/narrow", "Narrow window manga")
            Injekt.get<MangaRepository>().setMangaCategories(manga.id, listOf(category))
        }
    }

    @Test
    fun `the library search field keeps its height in a narrow window`() {
        val wide = libraryBarHeights(width = 1200)
        val narrow = libraryBarHeights(width = DesktopWindowPreferences.MIN_WIDTH)
        narrow shouldBe wide
    }

    @Test
    fun `the browse tabs keep one line and scroll in a narrow window`() {
        val wide = browseTabRowHeight(width = 1200, expectedTag = TAB_ROW_FIXED_TAG)
        val narrow = browseTabRowHeight(width = DesktopWindowPreferences.MIN_WIDTH, expectedTag = TAB_ROW_SCROLLABLE_TAG)
        narrow shouldBe wide
    }

    @Test
    fun `tabs fit when every one gets its widest title and padding`() {
        val tab = 100.dp + TAB_HORIZONTAL_PADDING * 2
        tabsFitEqually(count = 4, widestTitle = 100.dp, available = tab * 4) shouldBe true
        tabsFitEqually(count = 4, widestTitle = 100.dp, available = tab * 4 - 1.dp) shouldBe false
        tabsFitEqually(count = 0, widestTitle = 0.dp, available = 0.dp) shouldBe true
    }

    /** The search field's height, empty (placeholder) and with a long query. */
    private fun libraryBarHeights(width: Int): Pair<Int, Int> {
        var heights = 0 to 0
        runDesktopComposeUiTest(width = width, height = 700) {
            setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
            waitForTag("library-search")
            val empty = onNodeWithTag("library-search").fetchSemanticsNode().size.height
            onNodeWithTag("library-search").performTextInput("a query far longer than the narrow search field can show")
            waitForIdle()
            heights = empty to onNodeWithTag("library-search").fetchSemanticsNode().size.height
        }
        return heights
    }

    private fun browseTabRowHeight(width: Int, expectedTag: String): Int {
        var height = 0
        runDesktopComposeUiTest(width = width, height = 700) {
            val shortcuts = DesktopShortcuts(isMacOs = false)
            setContent { KomikkuApp(context = app, shortcuts = shortcuts) }
            waitForTag("library-search")
            runOnIdle { shortcuts.handler!!(DesktopShortcuts.Action.SelectTab(3)) }
            waitForTag(expectedTag)
            onAllNodesWithTag(if (expectedTag == TAB_ROW_FIXED_TAG) TAB_ROW_SCROLLABLE_TAG else TAB_ROW_FIXED_TAG)
                .assertCountEquals(0)
            onAllNodesWithText("JavaScript", substring = true).fetchSemanticsNodes().isNotEmpty() shouldBe true
            height = onNodeWithTag(expectedTag).fetchSemanticsNode().size.height
        }
        return height
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val SOURCE_ID = 4712L
    }
}
