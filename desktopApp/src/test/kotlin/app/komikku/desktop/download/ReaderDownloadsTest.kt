package app.komikku.desktop.download

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.FakeMangaServer
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderModel
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.1 review fixes, the reader's download rules (Android `ReaderViewModel`): "delete after reading" runs
 * every time the last page is reached (also for a chapter that was already read), and "download ahead" is
 * re-evaluated on later pages when a first check failed (next chapter not downloaded yet).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ReaderDownloadsTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 4)
    private lateinit var manga: Manga
    private lateinit var chapters: List<Chapter>

    private val downloadManager get() = Injekt.get<DownloadManager>()
    private val preferences get() = Injekt.get<DownloadPreferences>()

    @BeforeAll
    fun boot() {
        // Not a @TempDir: background download-cache writes may still run when JUnit would delete it.
        val dir = File(System.getProperty("komikku.dataDir"), "reader-downloads").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 4))) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/reader-dl", "Reader downloads manga")
        // Numbers 4 … 1, like a source listing (newest first)
        chapters = TestLibrary.insertChapters(
            manga,
            "/chapter/4" to "Chapter 4",
            "/chapter/3" to "Chapter 3",
            "/chapter/2" to "Chapter 2",
            "/chapter/1" to "Chapter 1",
        )
        preferences.saveChaptersAsCBZ().set(false)
        preferences.splitTallImages().set(false)
    }

    @AfterAll
    fun stop() = server.close()

    private fun chapter(n: Int) = chapters.first { it.url == "/chapter/$n" }

    private fun isDownloaded(chapter: Chapter) =
        downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, manga.source, true)

    private suspend fun awaitCondition(what: String, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(50) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what (queue: ${downloadManager.queueState.value.map { it.status }})", e)
        }
    }

    private suspend fun download(chapter: Chapter) {
        downloadManager.downloadChapters(manga, listOf(chapter))
        awaitCondition("${chapter.name} downloaded") { isDownloaded(chapter) && downloadManager.queueState.value.isEmpty() }
    }

    @Test
    @Order(1)
    fun `delete after reading also for a chapter that was already read`() = runBlocking<Unit> {
        preferences.removeAfterReadSlots().set(0)
        preferences.autoDownloadWhileReading().set(0)
        download(chapter(1))
        Injekt.get<ChapterRepository>().update(ChapterUpdate(id = chapter(1).id, read = true))

        val reader = ReaderModel(manga.id, chapter(1).id, initialPage = 0)
        reader.init().getOrThrow()
        reader.state.value.pages.size shouldBe 4
        reader.moveToLastPage()
        reader.close()
        awaitCondition("read chapter 1 deleted") { !isDownloaded(chapter(1)) }
    }

    @Test
    @Order(2)
    fun `download ahead is retried after a failed check`() = runBlocking<Unit> {
        preferences.removeAfterReadSlots().set(-1)
        // Android takes the next chapters from the next one on: 2 = chapter 3 (already downloaded) + chapter 4
        preferences.autoDownloadWhileReading().set(2)
        download(chapter(2))

        val reader = ReaderModel(manga.id, chapter(2).id, initialPage = 0)
        reader.init().getOrThrow()
        reader.state.value.viewerChapters!!.nextChapter!!.chapter.id shouldBe chapter(3).id
        // Page 2 of 4 (past 25 %): chapter 3 is not downloaded, so nothing is requested
        reader.moveTo(2)
        delay(500)
        downloadManager.queueState.value.isEmpty() shouldBe true
        isDownloaded(chapter(4)) shouldBe false

        // Once chapter 3 is downloaded, the next page requests chapter 4
        download(chapter(3))
        reader.moveTo(3)
        awaitCondition("chapter 4 downloaded ahead") { isDownloaded(chapter(4)) }
        reader.close()
    }
}
