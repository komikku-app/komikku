package app.komikku.desktop.download

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.FakeMangaServer
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
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
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * 8.1: the borrowed Android download stack on desktop — queue, downloader (desktop `DownloadJob`
 * coroutine runner), folder layout, ComicInfo, CBZ archiving, download cache, reading the result with the
 * borrowed `ChapterLoader`, queue operations, pending deletion and deletion. Pages come from an in-test
 * `HttpSource` over `MockWebServer`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DownloadManagerTest {

    private lateinit var app: Application
    private lateinit var dataDir: File
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private lateinit var source: FakeHttpSource
    private lateinit var manga: Manga
    private lateinit var chapters: List<Chapter>

    private val downloadManager get() = Injekt.get<DownloadManager>()
    private val provider get() = Injekt.get<DownloadProvider>()
    private val preferences get() = Injekt.get<DownloadPreferences>()

    @BeforeAll
    fun boot() {
        // Not a @TempDir: background download-cache writes may still run when JUnit would delete it.
        val dir = File(System.getProperty("komikku.dataDir"), "download-manager").apply { mkdirs() }
        dataDir = dir
        app = DesktopInjekt.init(dir)
        source = FakeHttpSource(server.baseUrl)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(source)) }
        manga = TestLibrary.insertManga(source.id, "/manga/dl", "Download manga")
        chapters = TestLibrary.insertChapters(
            manga,
            "/chapter/1" to "Chapter 1",
            "/chapter/2" to "Chapter 2",
            "/chapter/3" to "Chapter 3",
        )
        preferences.splitTallImages().set(false)
    }

    @AfterAll
    fun stop() = server.close()

    private fun chapter(n: Int) = chapters.first { it.url == "/chapter/$n" }

    private fun isDownloaded(chapter: Chapter, skipCache: Boolean = true) =
        downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, manga.source, skipCache)

    private suspend fun awaitCondition(what: String, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(50) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what (queue: ${downloadManager.queueState.value.map { it.status }})", e)
        }
    }

    @Test
    @Order(1)
    fun `folder download - pages, ComicInfo, Android folder layout, cache`() = runBlocking<Unit> {
        preferences.saveChaptersAsCBZ().set(false)
        downloadManager.downloadChapters(manga, listOf(chapter(1)))
        awaitCondition("chapter 1 downloaded") { isDownloaded(chapter(1)) && downloadManager.queueState.value.isEmpty() }

        val chapterDir = provider.findChapterDir(chapter(1).name, null, chapter(1).url, manga.ogTitle, source)!!
        chapterDir.isDirectory shouldBe true
        // <storage>/downloads/<source>/<manga>/<chapter>, as on Android
        val path = File(chapterDir.filePath!!)
        path.parentFile.name shouldBe "Download manga"
        path.parentFile.parentFile.name shouldBe provider.getSourceDirName(source)
        path.parentFile.parentFile.parentFile.name shouldBe "downloads"
        val names = chapterDir.listFiles()!!.map { it.name!! }
        names shouldContainAll listOf("001.png", "002.png", "003.png", "ComicInfo.xml")
        chapterDir.findFile("ComicInfo.xml")!!.openInputStream().reader().readText().let {
            it shouldContain "<Title>Chapter 1</Title>"
            it shouldContain "<Series>Download manga</Series>"
        }
        awaitCondition("download cache updated") { isDownloaded(chapter(1), skipCache = false) }
        downloadManager.getDownloadCount(manga) shouldBe 1
    }

    @Test
    @Order(2)
    fun `cbz download is archived`() = runBlocking<Unit> {
        preferences.saveChaptersAsCBZ().set(true)
        downloadManager.downloadChapters(manga, listOf(chapter(2)))
        awaitCondition("chapter 2 downloaded") { isDownloaded(chapter(2)) && downloadManager.queueState.value.isEmpty() }

        val cbz = provider.findChapterDir(chapter(2).name, null, chapter(2).url, manga.ogTitle, source)!!
        cbz.isFile shouldBe true
        cbz.name shouldBe "Chapter 2.cbz"
        ZipFile(File(cbz.filePath!!)).use { zip ->
            zip.entries().toList().map { it.name } shouldContainAll listOf("001.png", "002.png", "003.png", "ComicInfo.xml")
        }
        // No leftover temporary folder
        File(cbz.filePath!!).parentFile.listFiles()!!.map { it.name }.none { it.endsWith("_tmp") } shouldBe true
    }

    @Test
    @Order(3)
    fun `the reader loads downloaded folder and cbz chapters`() = runBlocking<Unit> {
        val loader = ChapterLoader(
            context = app,
            downloadManager = downloadManager,
            downloadProvider = provider,
            manga = manga,
            source = source,
            sourceManager = Injekt.get(),
            readerPrefs = Injekt.get(),
            mergedReferences = emptyList(),
            mergedManga = null,
        )
        val requestsBefore = server.requests.size
        listOf(chapter(1), chapter(2)).forEach { chapter ->
            val readerChapter = ReaderChapter(chapter).also { it.ref() }
            loader.loadChapter(readerChapter)
            readerChapter.pageLoader!!::class.simpleName shouldStartWith "Download"
            val pages = readerChapter.pages!!
            pages shouldHaveSize 3
            pages.all { it.status == Page.State.Ready } shouldBe true
            pages.map { p -> p.stream!!().use { ImageIO.read(it).height } } shouldBe listOf(101, 102, 103)
            readerChapter.unref()
        }
        server.requests.size shouldBe requestsBefore
    }

    @Test
    @Order(4)
    fun `queue operations - reorder, cancel, clear without starting`() = runBlocking<Unit> {
        val other = TestLibrary.insertChapters(manga, "/chapter/4" to "Chapter 4", "/chapter/5" to "Chapter 5")
        downloadManager.downloadChapters(manga, listOf(chapter(3)) + other, autoStart = false)
        val queue = downloadManager.queueState.value
        queue shouldHaveSize 3
        queue.all { it.status == Download.State.QUEUE } shouldBe true
        downloadManager.isRunning shouldBe false

        downloadManager.reorderQueue(queue.reversed())
        downloadManager.queueState.value.map { it.chapter.id } shouldBe queue.reversed().map { it.chapter.id }

        downloadManager.cancelQueuedDownloads(listOf(downloadManager.queueState.value.first()))
        downloadManager.queueState.value shouldHaveSize 2

        downloadManager.clearQueue()
        downloadManager.queueState.value shouldHaveSize 0
        downloadManager.isRunning shouldBe false
    }

    @Test
    @Order(5)
    fun `pending deletion (delete after reading) and delete`() = runBlocking<Unit> {
        val read = chapter(1).copy(read = true)
        downloadManager.enqueueChaptersToDelete(listOf(read), manga)
        isDownloaded(chapter(1)) shouldBe true
        downloadManager.deletePendingChapters()
        awaitCondition("chapter 1 deleted") { !isDownloaded(chapter(1)) }

        downloadManager.deleteChapters(listOf(chapter(2)), manga, source, ignoreCategoryExclusion = true)
        awaitCondition("chapter 2 deleted") { !isDownloaded(chapter(2)) }
        // The empty manga folder goes away too, like on Android
        awaitCondition("manga folder deleted") { provider.findMangaDir(manga.ogTitle, source) == null }
        awaitCondition("download cache updated") { downloadManager.getDownloadCount(manga) == 0 }
    }
}
