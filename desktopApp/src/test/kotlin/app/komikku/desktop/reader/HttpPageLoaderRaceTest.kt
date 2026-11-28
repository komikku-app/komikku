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
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * R.2 (REVIEW_STEP_D1.md): the borrowed `HttpPageLoader` runs two reader threads (`eh_reader_threads`) over one queue
 * that can hold a page more than once (each `loadPage` caller queues it; the paged viewer's preload and its shown slot
 * both call it). Two threads that both take the page before either marks it as downloading both download it; the
 * chapter cache's editor goes to the first, the second's `putImageToCache` returns without writing and marks the page
 * ready while the image is not in the cache yet. The reader then fails to decode it and, the page staying ready, never
 * tries again (`ReaderWidePagesUiTest` timed out on such a page in run 46117892032).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpPageLoaderRaceTest {

    private val imageRequests = AtomicInteger()
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private lateinit var app: Application
    private lateinit var online: FakeHttpSource

    @BeforeAll
    fun boot() {
        val pages = server.server.dispatcher
        server.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = pages.dispatch(request)
                if (!request.url.encodedPath.endsWith("/3.png")) return response
                // The first download of the last page holds the cache's editor for a second
                return if (imageRequests.incrementAndGet() == 1) response.newBuilder().bodyDelay(1, TimeUnit.SECONDS).build() else response
            }
        }
        app = DesktopInjekt.init(File(System.getProperty("komikku.dataDir"), "http-page-loader-race").apply { mkdirs() })
        online = FakeHttpSource(server.baseUrl)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(online)) }
    }

    @AfterAll
    fun stop() = server.close()

    @Test
    fun `a page asked for twice at once is downloaded once and is ready only once its image is in the cache`(): Unit = runBlocking {
        val manga = TestLibrary.insertManga(online.id, "/manga/race", "Race manga")
        val chapter = ReaderChapter(TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1").single()).also { it.ref() }
        ChapterLoader(
            context = app,
            downloadManager = Injekt.get<DownloadManager>(),
            downloadProvider = Injekt.get<DownloadProvider>(),
            manga = manga,
            source = online,
            sourceManager = Injekt.get(),
            readerPrefs = Injekt.get(),
            mergedReferences = emptyList(),
            mergedManga = null,
        ).loadChapter(chapter)
        // The last page: `loadPage` queues no following pages, so both reader threads take an entry of this one
        val page = chapter.pages!!.last()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val imageAtFirstReady = scope.async {
                page.statusFlow.first { it == Page.State.Ready }
                runCatching { page.stream!!().use { it.readBytes().size } }
            }
            // The threads check the chapter cache right after taking an entry. Holding the cache's lock keeps a thread
            // that took the page from marking it as downloading until the other thread had the chance to take it too.
            val diskCache = ChapterCache::class.java.getDeclaredField("diskCache")
                .apply { isAccessible = true }
                .get(Injekt.get<ChapterCache>())
            synchronized(diskCache) {
                repeat(2) { scope.launch { chapter.pageLoader!!.loadPage(page) } }
                awaitThreadsInPageLoad(atLeast = 1)
                Thread.sleep(500)
            }
            withTimeout(15_000) { imageAtFirstReady.await() }.getOrThrow() shouldBeGreaterThan 0
            Thread.sleep(1_500)
            imageRequests.get() shouldBe 1
        } finally {
            scope.cancel()
            chapter.unref()
        }
    }

    /** Waits until [atLeast] reader threads wait for the chapter cache's lock inside the page download. */
    private fun awaitThreadsInPageLoad(atLeast: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = Thread.getAllStackTraces().count { (thread, stack) ->
                thread.state == Thread.State.BLOCKED && stack.any { it.methodName == "internalLoadPage" }
            }
            if (waiting >= atLeast) return
            Thread.sleep(20)
        }
        error("No reader thread reached the page download within 10 s")
    }
}
