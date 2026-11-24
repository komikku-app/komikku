package app.komikku.desktop.reader

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.PageHalf
import app.komikku.desktop.ui.reader.ReaderDisplay
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.ui.reader.splitSide
import app.komikku.shared.reader.image.PageFit
import app.komikku.shared.reader.image.PixelSize
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.abs

/**
 * P11, headless: wide pages in the desktop paged viewer, Android's rules (`PagerPageHolder`, SY's double pages). A
 * five-page chapter whose page 3 is wide (200 × 100): double pages show it alone and pair the others around it, also
 * backwards; "Split wide pages" shows its two halves one after the other (the second half saves no progress); "Rotate
 * wide pages to fit" turns it so it is taller than wide. Plus the side and layout rules.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderWidePagesUiTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 5, widePages = setOf(3))
    private lateinit var manga: Manga
    private lateinit var chapter: Chapter
    private val preferences get() = Injekt.get<ReaderPreferences>()

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "reader-wide-pages").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 1))) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/wide", "Wide manga")
        chapter = TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1").single()
    }

    /** Single pages unless a test says otherwise (the default, automatic, means double pages in a landscape window). */
    @BeforeEach
    fun reset() {
        preferences.pageLayout().set(PagerConfig.PageLayout.SINGLE_PAGE)
        preferences.dualPageSplitPaged().set(false)
        preferences.dualPageRotateToFit().set(false)
        runBlocking { Injekt.get<ChapterRepository>().update(ChapterUpdate(id = chapter.id, read = false, lastPageRead = 0)) }
    }

    @AfterAll
    fun stop() = server.close()

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.shown(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.next() = onNodeWithTag("reader").performKeyInput { pressKey(Key.PageDown) }

    private fun ComposeUiTest.previous() = onNodeWithTag("reader").performKeyInput { pressKey(Key.PageUp) }

    private fun ComposeUiTest.open() {
        setContent { KomikkuApp(context = app) }
        runOnIdle { Injekt.get<ReaderLauncher>().open(manga.id, chapter.id, 0) }
        waitForTag("paged-viewer")
        waitForText("1 / 5")
    }

    private fun lastPageRead() = runBlocking { Injekt.get<ChapterRepository>().getChapterById(chapter.id)!!.lastPageRead }

    @Test
    fun `double pages show a wide page alone`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        preferences.pageLayout().set(PagerConfig.PageLayout.DOUBLE_PAGES)
        open()
        waitForTag("page-tile-0")
        shown("page-0") shouldBe true
        shown("page-1") shouldBe true
        // Pages 1 + 2, then the wide page 3 alone (known from the preloaded neighbours), then 4 + 5
        next()
        waitForText("3 / 5")
        waitUntil(timeoutMillis = 15_000) { shown("page-2") && !shown("page-3") }
        next()
        waitForText("4 / 5")
        waitUntil(timeoutMillis = 15_000) { shown("page-3") && shown("page-4") }
        // Backwards the same spreads
        previous()
        waitForText("3 / 5")
        waitUntil(timeoutMillis = 15_000) { shown("page-2") && !shown("page-1") }
        previous()
        waitForText("1 / 5")
        waitUntil(timeoutMillis = 15_000) { shown("page-0") && shown("page-1") }
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }

    @Test
    fun `split wide pages shows both halves in turn`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        preferences.dualPageSplitPaged().set(true)
        open()
        next()
        waitForText("2 / 5")
        next()
        waitForText("3 / 5")
        waitForTag("page-2")
        // The first half: half the wide page, so about square
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("page-2").fetchSemanticsNodes().firstOrNull()?.size?.let { abs(it.width - it.height) <= 2 } == true
        }
        waitUntil(timeoutMillis = 10_000) { lastPageRead() == 2L }
        // The second half follows (once the split is known) and keeps the progress on page 3
        next()
        waitForTag("page-2-second-half")
        shown("page-3") shouldBe false
        Thread.sleep(300)
        lastPageRead() shouldBe 2L
        next()
        waitForText("4 / 5")
        waitForTag("page-3")
        waitUntil(timeoutMillis = 10_000) { lastPageRead() == 3L }
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }

    @Test
    fun `rotate wide pages to fit turns the wide page upright`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        preferences.dualPageRotateToFit().set(true)
        open()
        next()
        waitForText("2 / 5")
        next()
        waitForText("3 / 5")
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("page-2").fetchSemanticsNodes().firstOrNull()?.size?.let { it.height > it.width * 1.5f } == true
        }
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }

    @Test
    fun `split sides and the automatic layout follow Android`() {
        splitSide(ReadingMode.LEFT_TO_RIGHT, invert = false, first = true) shouldBe PageHalf.Left
        splitSide(ReadingMode.LEFT_TO_RIGHT, invert = false, first = false) shouldBe PageHalf.Right
        splitSide(ReadingMode.RIGHT_TO_LEFT, invert = false, first = true) shouldBe PageHalf.Right
        splitSide(ReadingMode.VERTICAL, invert = false, first = true) shouldBe PageHalf.Right
        splitSide(ReadingMode.RIGHT_TO_LEFT, invert = true, first = true) shouldBe PageHalf.Left
        val display = ReaderDisplay(PageFit.Screen, Color.Black, Color.White, null, 0, PagerConfig.PageLayout.AUTOMATIC)
        display.doublePages(PixelSize(1000, 700)) shouldBe true
        display.doublePages(PixelSize(700, 1000)) shouldBe false
        display.copy(splitWidePages = true).doublePages(PixelSize(1000, 700)) shouldBe false
        display.copy(pageLayout = PagerConfig.PageLayout.DOUBLE_PAGES).doublePages(PixelSize(700, 1000)) shouldBe true
    }
}
