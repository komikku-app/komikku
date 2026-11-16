package app.komikku.desktop.download

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.FakeMangaServer
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderLauncher
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.1 UI, headless: chapter download indicator (download, then delete from the selection), the
 * "download unread" menu, the download queue screen (cancel one, cancel all) and the "downloaded only"
 * toggle of the More tab filtering the library.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownloadUiTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 2)

    @BeforeAll
    fun boot() {
        // Not a @TempDir: background download-cache writes may still run when JUnit would delete it.
        // The test data dir (komikku.dataDir) is wiped before every test run.
        val dataDir = File(System.getProperty("komikku.dataDir"), "download-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 3))) }
        Injekt.get<DownloadPreferences>().saveChaptersAsCBZ().set(false)
        TestLibrary.insertManga(FakeHttpSource.ID, "/manga/dl-ui", "Downloads manga", favorite = true)
        TestLibrary.insertManga(FakeHttpSource.ID, "/manga/other", "Other manga", favorite = true)
    }

    @AfterAll
    fun stop() = server.close()

    private fun ComposeUiTest.waitForText(text: String, present: Boolean = true, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() == present
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun chapters(): List<Chapter> = runBlocking {
        val manga = Injekt.get<GetManga>().await("/manga/dl-ui", FakeHttpSource.ID)!!
        Injekt.get<ChapterRepository>().getChapterByMangaId(manga.id)
    }

    private fun chapter(name: String) = chapters().single { it.name == name }

    @Test
    fun `download from details, queue screen and downloaded-only library filter`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }

        // Library → details (chapters fetched and stored)
        waitForText("Downloads manga")
        onNodeWithText("Downloads manga").performClick()
        waitForText("Chapter 3")
        val c1 = chapter("Chapter 1")

        // Single chapter: arrow → queued → downloaded (check mark)
        onNodeWithTag("chapter-download-${c1.id}-NOT_DOWNLOADED").performClick()
        waitForTag("chapter-download-${c1.id}-DOWNLOADED")

        // "Download unread" queues the other two
        onNodeWithTag("details-download-menu").performClick()
        onNodeWithTag("download-action-UNREAD_CHAPTERS").performClick()
        listOf("Chapter 2", "Chapter 3").forEach { waitForTag("chapter-download-${chapter(it).id}-DOWNLOADED") }
        server.requests.count { it.startsWith("/img/") } shouldBe 6

        // Delete chapter 1's download from the selection bar
        onNodeWithTag("chapter-select-${c1.id}").performClick()
        onNodeWithTag("selection-delete").performClick()
        waitForTag("chapter-download-${c1.id}-NOT_DOWNLOADED")

        // Back to the library: both manga listed
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
        waitForText("Other manga")

        // Download queue screen: queued (not started) downloads, cancel one, cancel all
        val manga = runBlocking { Injekt.get<GetManga>().await("/manga/dl-ui", FakeHttpSource.ID)!! }
        val other = TestLibrary.insertChapters(manga, "/chapter/8" to "Chapter 8", "/chapter/9" to "Chapter 9")
        val downloadManager = Injekt.get<DownloadManager>()
        downloadManager.downloadChapters(manga, other, autoStart = false)
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-download-queue")
        onNodeWithTag("more-download-queue").performClick()
        waitForTag("download-queue")
        waitForText("Chapter 8")
        onNodeWithTag("download-cancel-${other.first { it.name == "Chapter 8" }.id}").performClick()
        waitForText("Chapter 8", present = false)
        downloadManager.queueState.value.size shouldBe 1
        onNodeWithTag("queue-clear").performClick()
        waitForText(MR.strings.information_no_downloads.localized())
        downloadManager.queueState.value.size shouldBe 0

        // "Downloaded only" (More home) hides the manga without downloads from the library
        Injekt.get<BasePreferences>().downloadedOnly().get() shouldBe false
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
        waitForTag("more-downloaded-only")
        onNodeWithTag("more-downloaded-only").performClick()
        waitUntil(timeoutMillis = 5_000) { Injekt.get<BasePreferences>().downloadedOnly().get() }
        onNodeWithText(MR.strings.label_library.localized()).performClick()
        waitForText("Downloads manga")
        waitForText("Other manga", present = false)
        Unit
    }
}
