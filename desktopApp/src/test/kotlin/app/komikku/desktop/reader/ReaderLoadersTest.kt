package app.komikku.desktop.reader

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import javax.imageio.ImageIO

/**
 * 7.1: the borrowed `ChapterLoader` and page loaders on desktop — HTTP through an in-test `HttpSource`
 * and [FakeMangaServer] (page list, image download into the desktop `ChapterCache`, page list cache
 * on recycle), and the local source's archive (generated CBZ) and directory chapters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderLoadersTest {

    private lateinit var app: Application
    private lateinit var dataDir: File
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private lateinit var online: FakeHttpSource
    private lateinit var local: LocalSource

    @BeforeAll
    fun boot(@TempDir dir: File) {
        dataDir = dir
        app = DesktopInjekt.init(dir)
        online = FakeHttpSource(server.baseUrl)
        local = LocalSource(app, Injekt.get<LocalSourceFileSystem>(), Injekt.get<LocalCoverManager>(), allowHiddenFiles = { false })
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(online, local)) }
    }

    @AfterAll
    fun stop() = server.close()

    private fun loaderFor(manga: Manga) = ChapterLoader(
        context = app,
        downloadManager = Injekt.get<DownloadManager>(),
        downloadProvider = Injekt.get<DownloadProvider>(),
        manga = manga,
        source = Injekt.get<SourceManager>().getOrStub(manga.source),
        sourceManager = Injekt.get(),
        readerPrefs = Injekt.get(),
        mergedReferences = emptyList(),
        mergedManga = null,
    )

    /** Starts `loadPage` (which never returns, like on Android) and waits for the page to be ready. */
    private fun ReaderPage.loadAndAwait(scope: CoroutineScope) = runBlocking {
        scope.launch { chapter.pageLoader!!.loadPage(this@loadAndAwait) }
        withTimeout(15_000) { statusFlow.first { it == Page.State.Ready || it is Page.State.Error } }
        status shouldBe Page.State.Ready
    }

    @Test
    fun `http chapter - page list, images through the chapter cache, page list cached on recycle`() = runBlocking {
        val manga = TestLibrary.insertManga(online.id, "/manga/http", "HTTP manga")
        val chapter = TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1").single()
        val readerChapter = ReaderChapter(chapter)
        readerChapter.ref()
        loaderFor(manga).loadChapter(readerChapter)

        val pages = readerChapter.pages!!
        pages shouldHaveSize 3
        pages.map { it.imageUrl } shouldBe (1..3).map { "${server.baseUrl}/img/1/$it.png" }
        server.requests.count { it == "/chapter/1" } shouldBe 1

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            pages[0].loadAndAwait(scope)
            val image = pages[0].stream!!().use { ImageIO.read(it) }
            image.height shouldBe 101
            Injekt.get<ChapterCache>().isImageInCache(pages[0].imageUrl!!) shouldBe true
        } finally {
            scope.cancel()
        }

        // Recycling stores the page list; a new loader reads it from the cache, not the network.
        readerChapter.unref()
        val again = ReaderChapter(chapter).also { it.ref() }
        var cached = false
        repeat(50) {
            if (!cached) {
                Thread.sleep(100)
                cached = runCatching { Injekt.get<ChapterCache>().getPageListFromCache(chapter) }.isSuccess
            }
        }
        cached shouldBe true
        loaderFor(manga).loadChapter(again)
        again.pages!! shouldHaveSize 3
        server.requests.count { it == "/chapter/1" } shouldBe 1
        again.unref()
    }

    @Test
    fun `local archive and directory chapters`() = runBlocking {
        val mangaDir = dataDir.resolve("storage/local/Local manga").apply { mkdirs() }
        TestImages.cbz(mangaDir.resolve("Chapter 1.cbz"), count = 4)
        mangaDir.resolve("Chapter 2").apply {
            mkdirs()
            (1..2).forEach { resolve("%03d.jpg".format(it)).writeBytes(TestImages.encode(30, 50, "jpg")) }
        }
        val manga = TestLibrary.insertManga(LocalSource.ID, "Local manga", "Local manga")
        val (archive, directory) = TestLibrary.insertChapters(
            manga,
            "Local manga/Chapter 1.cbz" to "Chapter 1",
            "Local manga/Chapter 2" to "Chapter 2",
        )
        val loader = loaderFor(manga)

        val archiveChapter = ReaderChapter(archive).also { it.ref() }
        loader.loadChapter(archiveChapter)
        val archivePages = archiveChapter.pages!!
        archivePages shouldHaveSize 4
        archivePages.all { it.status == Page.State.Ready } shouldBe true
        archivePages.map { p -> p.stream!!().use { ImageIO.read(it).height } } shouldBe listOf(61, 62, 63, 64)
        archiveChapter.pageLoader!!::class.simpleName shouldStartWith "Archive"
        archiveChapter.unref()

        val directoryChapter = ReaderChapter(directory).also { it.ref() }
        loader.loadChapter(directoryChapter)
        directoryChapter.pages!! shouldHaveSize 2
        directoryChapter.pages!!.first().stream!!().use { ImageIO.read(it).width } shouldBe 30
        directoryChapter.pageLoader!!::class.simpleName shouldStartWith "Directory"
        directoryChapter.unref()
    }
}
