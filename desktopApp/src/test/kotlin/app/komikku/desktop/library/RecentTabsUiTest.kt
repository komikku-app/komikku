package app.komikku.desktop.library

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.FakeMangaServer
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.shared.library.LibraryUpdateRunner
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 6L.2 headless: library refresh through the shared runner (progress, chapters stored), Updates tab
 * (grouped by day, opens the reader), History tab (entries after reading, resume, remove one, clear all).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecentTabsUiTest {

    private lateinit var app: Application
    private lateinit var manga: Manga
    private val server = FakeMangaServer(pagesPerChapter = 2)

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl))) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/recent", "Recent manga", favorite = true)
    }

    @AfterAll
    fun stop() = server.close()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun chapters(): List<Chapter> = runBlocking { Injekt.get<ChapterRepository>().getChapterByMangaId(manga.id) }

    private fun ComposeUiTest.readAndClose(openTag: String) {
        onNodeWithTag(openTag).performClick()
        waitForTag("reader")
        waitForText("1 / 2")
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }

    @Test
    fun `refresh library, read from updates, manage history`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        // Library refresh (all) through the shared runner
        waitForText("Recent manga")
        chapters().size shouldBe 0
        onNodeWithTag("update-library-all").performClick()
        val runner = Injekt.get<LibraryUpdateRunner>()
        waitUntil(timeoutMillis = 15_000) { runner.progress.value?.finished == true }
        runner.progress.value!!.let {
            it.total shouldBe 1
            it.newChapters shouldBe 2
            it.failed shouldBe emptyList()
        }
        chapters().map { it.name }.toSet() shouldBe setOf("Chapter 1", "Chapter 2")
        waitForTag("library-update-progress")

        // Updates: today's chapters, open one in the reader
        onNodeWithText(MR.strings.label_recent_updates.localized()).performClick()
        waitForText(MR.strings.relative_time_today.localized())
        val c1 = chapters().single { it.name == "Chapter 1" }
        val c2 = chapters().single { it.name == "Chapter 2" }
        waitForTag("update-${c2.id}")
        readAndClose("update-${c1.id}")
        readAndClose("update-${c2.id}")
        runBlocking { Injekt.get<GetHistory>().await(manga.id).map { it.chapterId }.toSet() } shouldBe setOf(c1.id, c2.id)

        // History (latest chapter per manga, like Android): resume, remove the entry, clear all
        onNodeWithText(MR.strings.label_recent_manga.localized()).performClick()
        waitForTag("history-${c2.id}")
        readAndClose("history-resume-${c2.id}")
        onNodeWithTag("history-remove-${c2.id}").performClick()
        waitForTag("history-${c2.id}", present = false)
        onNodeWithTag("clear-history").performClick()
        onNodeWithTag("clear-history-confirm").performClick()
        waitForText(MR.strings.information_no_recent_manga.localized())
        Unit
    }
}
