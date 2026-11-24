package app.komikku.desktop.ui.browse

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestImages
import app.komikku.desktop.reader.TestLibrary
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.mihon.JsPageRef
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.mihon.MangayomiJsSource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO

/**
 * A Mangayomi JavaScript source built by [MangayomiJsRuntime] (with the desktop app's network client and preference
 * files) handed to the existing desktop code paths through a fixed source manager: browse (popular, search) → details
 * → add to library → its settings screen from `getSourcePreferences()`, and the reader's chapter loader (page list,
 * image through the chapter cache with the source's headers, page list cache).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsSourceBrowseUiTest {

    private lateinit var app: Application
    private lateinit var runtime: MangayomiJsRuntime
    private lateinit var source: MangayomiJsSource
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                val html = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8")
                return when {
                    path == "/list" -> html.body(
                        """<html><body>
                          <div class="item"><a href="/manga/first">First JS manga</a><img src="/covers/first.png"></div>
                          <div class="item"><a href="/manga/second">Second JS manga</a><img src="/covers/second.png"></div>
                        </body></html>""",
                    ).build()
                    path == "/search" -> html.body(
                        """<html><body><div class="item"><a href="/manga/found">Found ${request.url.queryParameter("q")}</a>
                           <img src="/covers/found.png"></div></body></html>""",
                    ).build()
                    path.startsWith("/manga/") -> html.body(
                        """<html><body><h1>${path.substringAfterLast('/')} title</h1><p class="summary">A JS description</p>
                           <ul><li><a href="/chapter/2">Chapter 2</a></li><li><a href="/chapter/1">Chapter 1</a></li></ul></body></html>""",
                    ).build()
                    path.startsWith("/chapter/") -> MockResponse.Builder().addHeader("Content-Type", "application/json")
                        .body("""["/img/1.png", "/img/2.png"]""").build()
                    // Images need the source's Referer (from getHeaders), like many real sites.
                    path.startsWith("/img/") || path.startsWith("/covers/") ->
                        if (request.headers["Referer"] != server.url("/").toString()) {
                            MockResponse.Builder().code(403).build()
                        } else {
                            MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(TestImages.encode(40, 60))).build()
                        }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()

        app = DesktopInjekt.init(dataDir)
        // A source opens its catalogue directly here (the source feed is SourceFeedUiTest's)
        Injekt.get<UiPreferences>().useNewSourceNavigation().set(false)
        runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>())
        val descriptor = JsSourceDescriptor(
            id = MANGAYOMI_ID,
            name = "JS Test Source",
            lang = "en",
            baseUrl = server.url("/").toString(),
            sourceCodeUrl = "https://raw.example.org/js-test.js",
        )
        source = runBlocking { runtime.source(descriptor, CODE) }
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(source)) }
    }

    @AfterAll
    fun stop() {
        // Unregisters the source's preference listener and cancels its refresh scope.
        if (::runtime.isInitialized) runtime.close()
        server.close()
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `browse a JavaScript source, open details, add to library, change its settings`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForText("JS Test Source")
        onNodeWithText("JS Test Source").performClick()
        waitForText("First JS manga")
        waitForText("Second JS manga")
        // supportsLatest comes from the source's switch preference (default on).
        waitForText(MR.strings.latest.localized())

        onNode(hasSetTextAction()).performTextInput("abc")
        onNodeWithContentDescription(MR.strings.action_search.localized()).performClick()
        waitForText("Found abc")

        onNodeWithText("Found abc").performClick()
        waitForText("A JS description")
        waitForText("Chapter 2")
        waitForText("Chapter 1")
        onNodeWithText(MR.strings.add_to_library.localized()).performClick()
        waitForText(MR.strings.in_library.localized())
        val stored = runBlocking { Injekt.get<GetManga>().await("/manga/found", source.id) }!!
        stored.favorite shouldBe true
        stored.description shouldBe "A JS description"
        stored.title shouldBe "found title"
        stored.thumbnailUrl shouldBe server.url("/covers/found.png").toString()

        // Settings from getSourcePreferences(); turning latest off refreshes the source's metadata.
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
        waitForText("Found abc")
        onNodeWithContentDescription(MR.strings.label_settings.localized()).performClick()
        waitForText("Show latest")
        val prefs = app.getSharedPreferences(JsSourceIds.preferencesName(source.id), Context.MODE_PRIVATE)
        onNodeWithTag("preference-latest").performClick()
        waitUntil(timeoutMillis = 5_000) { !prefs.getBoolean("latest", true) }
        runBlocking { source.awaitMetadata() }.supportsLatest shouldBe false
        source.supportsLatest shouldBe false
    }

    @Test
    fun `read a chapter of a JavaScript source through the chapter loader`() = runBlocking {
        val manga = TestLibrary.insertManga(source.id, "/manga/reader", "Reader manga")
        val chapter = TestLibrary.insertChapters(manga, "/chapter/7" to "Chapter 7").single()
        val readerChapter = ReaderChapter(chapter).also { it.ref() }
        loaderFor(manga).loadChapter(readerChapter)

        val pages = readerChapter.pages!!
        pages shouldHaveSize 2
        pages.map { it.imageUrl } shouldBe listOf(server.url("/img/1.png").toString(), server.url("/img/2.png").toString())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            scope.launch { readerChapter.pageLoader!!.loadPage(pages[0]) }
            withTimeout(20_000) { pages[0].statusFlow.first { it == Page.State.Ready || it is Page.State.Error } }
            pages[0].status shouldBe Page.State.Ready
            pages[0].stream!!().use { ImageIO.read(it) }.height shouldBe 60
            Injekt.get<ChapterCache>().isImageInCache(pages[0].imageUrl!!) shouldBe true
        } finally {
            scope.cancel()
        }

        // Recycling stores the page list with Page.url, which still carries the image URL.
        readerChapter.unref()
        var cached: List<Page>? = null
        repeat(50) {
            if (cached == null) {
                Thread.sleep(100)
                cached = runCatching { Injekt.get<ChapterCache>().getPageListFromCache(chapter) }.getOrNull()
            }
        }
        cached!!.map { JsPageRef.decode(it.url)?.url } shouldBe pages.map { it.imageUrl }
        Unit
    }

    @Test
    fun `a JavaScript source has a download folder of its own`() {
        val provider = Injekt.get<DownloadProvider>()
        val folder = provider.getSourceDirName(source)
        folder shouldBe "JS Test Source [JS] (EN)"
        // A Mihon source for the same site (same name and language) gets another folder.
        provider.getSourceDirName(StubSource(id = 1, lang = source.lang, name = source.name)) shouldNotBe folder
        // The stub registered for an uninstalled JavaScript source keeps its folder.
        provider.getSourceDirName(StubSource(id = source.id, lang = source.lang, name = source.stubName)) shouldBe folder
    }

    private fun loaderFor(manga: tachiyomi.domain.manga.model.Manga) = ChapterLoader(
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

    private companion object {
        const val MANGAYOMI_ID = 4242L

        /** A Mangayomi-style extension: HTML lists through the DOM bridge, preferences, filters, headers. */
        val CODE = """
            const mangayomiSources = [{ "name": "JS Test Source", "lang": "en", "id": 4242 }];
            class DefaultExtension extends MProvider {
              base() { return this.source.baseUrl.replace(/\/+$/, ""); }
              get supportsLatest() { return new SharedPreferences().get("latest"); }
              getHeaders(url) { return { "Referer": this.base() + "/" }; }
              items(body) {
                return new Document(body).select("div.item").map((e) => ({
                  name: e.selectFirst("a").text,
                  link: e.selectFirst("a").attr("href"),
                  imageUrl: e.selectFirst("img").attr("src"),
                }));
              }
              async getPopular(page) {
                const res = await new Client().get(this.base() + "/list?page=" + page, this.getHeaders());
                return { list: this.items(res.body), hasNextPage: false };
              }
              async getLatestUpdates(page) { return this.getPopular(page); }
              async search(query, page, filters) {
                const res = await new Client().get(this.base() + "/search?q=" + encodeURIComponent(query), this.getHeaders());
                return { list: this.items(res.body), hasNextPage: false };
              }
              async getDetail(url) {
                const doc = new Document((await new Client().get(this.base() + url, this.getHeaders())).body);
                return {
                  name: doc.selectFirst("h1").text,
                  description: doc.selectFirst("p.summary").text,
                  status: 0,
                  chapters: doc.select("li a").map((a) => ({ name: a.text, url: a.attr("href") })),
                };
              }
              async getPageList(url) {
                const res = await new Client().get(this.base() + url, this.getHeaders());
                return JSON.parse(res.body).map((path) => this.base() + path);
              }
              getFilterList() { return [{ type_name: "TextFilter", name: "Author", state: "" }]; }
              getSourcePreferences() {
                return [{ key: "latest", switchPreferenceCompat: { title: "Show latest", summary: "", value: true } }];
              }
            }
        """.trimIndent()
    }
}
