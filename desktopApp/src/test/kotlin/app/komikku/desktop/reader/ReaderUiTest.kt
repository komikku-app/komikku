package app.komikku.desktop.reader

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless end-to-end test of 6L.1 + Phase 7 with an in-test online source whose pages and images come
 * from [FakeMangaServer] (generated PNGs): library → details (chapters fetched and stored through
 * `UpdateMangaFromRemote` / `SyncChaptersWithSource`, mark read / unread from the selection) → reader
 * (page list over HTTP, images decoded, page indicator) → page turns by keyboard → progress, read flag
 * and history in the database → back to details with "continue reading" on the next chapter.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderUiTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 3)

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl))) }
        TestLibrary.insertManga(FakeHttpSource.ID, "/manga/ui", "UI manga", favorite = true)
    }

    @AfterAll
    fun stop() = server.close()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun chapters(): List<Chapter> = runBlocking {
        Injekt.get<ChapterRepository>().getChapterByMangaId(Injekt.get<tachiyomi.domain.manga.interactor.GetManga>().await("/manga/ui", FakeHttpSource.ID)!!.id)
    }

    private fun chapter(name: String) = chapters().single { it.name == name }

    private fun ComposeUiTest.waitForDb(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        waitUntil(timeoutMillis = timeoutMillis) { condition() }
    }

    @Test
    fun `library to details to reader, page turns save progress and history`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        Injekt.get<app.komikku.desktop.ui.reader.ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }

        // Library → details: chapters are fetched from the source and stored
        waitForText("UI manga")
        onNodeWithText("UI manga").performClick()
        waitForText("Chapter 2")
        chapters().map { it.name }.toSet() shouldBe setOf("Chapter 1", "Chapter 2")
        waitForText(MR.strings.action_start.localized())

        // Selection: mark chapter 2 read, then unread again
        val c2 = chapter("Chapter 2")
        onNodeWithTag("chapter-select-${c2.id}").performClick()
        onNodeWithContentDescription(MR.strings.action_mark_as_read.localized()).performClick()
        waitForDb { chapter("Chapter 2").read }
        onNodeWithTag("chapter-select-${c2.id}").performClick()
        onNodeWithContentDescription(MR.strings.action_mark_as_unread.localized()).performClick()
        waitForDb { !chapter("Chapter 2").read }

        // Open chapter 1 in the reader: page list over HTTP, first image decoded
        val c1 = chapter("Chapter 1")
        onNodeWithTag("chapter-${c1.id}").performClick()
        waitForTag("reader")
        waitForText("1 / 3")
        waitForTag("page-tile-0")
        server.requests.contains("/chapter/1") shouldBe true

        // Page turns by keyboard save the last page read; the last page marks the chapter read
        onNodeWithTag("reader").performKeyInput { pressKey(Key.PageDown) }
        waitForText("2 / 3")
        waitForDb { chapter("Chapter 1").lastPageRead == 1L }
        chapter("Chapter 1").read shouldBe false
        onNodeWithTag("reader").performKeyInput { pressKey(Key.MoveEnd) }
        waitForText("3 / 3")
        waitForDb { chapter("Chapter 1").read }

        // Bookmark from the menu
        onNodeWithTag("reader").performKeyInput { pressKey(Key.M) }
        waitForTag("reader-bookmark")
        onNodeWithTag("reader-bookmark").performClick()
        waitForDb { chapter("Chapter 1").bookmark }

        // Escape closes the menu, then the reader; history is written on close
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader-bookmark", present = false)
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
        runBlocking { Injekt.get<GetHistory>().await(c1.mangaId).map { it.chapterId } } shouldBe listOf(c1.id)

        // Details: continue reading now points to chapter 2
        waitForText(MR.strings.action_resume.localized() + " · Chapter 2")
        onNodeWithTag("details-continue").performClick()
        waitForTag("reader")
        waitForText("1 / 3")
        Unit
    }

    @Test
    fun `settings switch the manga to webtoon, which scrolls and saves progress`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/webtoon", "Webtoon manga", favorite = true)
        val chapter = TestLibrary.insertChapters(manga, "/chapter/7" to "Chapter 7").single()
        setContent { KomikkuApp(context = app) }
        runOnIdle { Injekt.get<app.komikku.desktop.ui.reader.ReaderLauncher>().open(manga.id, chapter.id) }
        waitForTag("paged-viewer")
        waitForText("1 / 3")

        // Settings panel: reading mode stored in the manga's viewer flags (Android: SetMangaViewerFlags)
        onNodeWithTag("reader").performKeyInput { pressKey(Key.M) }
        waitForTag("reader-settings-button")
        onNodeWithTag("reader-settings-button").performClick()
        waitForTag("reading-mode-WEBTOON")
        onNodeWithTag("reading-mode-WEBTOON").performClick()
        waitForTag("webtoon-viewer")
        waitForDb {
            runBlocking { Injekt.get<tachiyomi.domain.manga.interactor.GetManga>().await(manga.id)!!.viewerFlags } and 0x7L == 0x4L
        }

        // Close settings + menu, then scroll to the end with the keyboard: progress follows the list
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader-settings", present = false)
        waitForTag("page-tile-0")
        onNodeWithTag("reader").performKeyInput { pressKey(Key.MoveEnd) }
        waitForDb {
            runBlocking { Injekt.get<ChapterRepository>().getChapterById(chapter.id)!!.lastPageRead } >= 1L
        }
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
        Unit
    }
}
