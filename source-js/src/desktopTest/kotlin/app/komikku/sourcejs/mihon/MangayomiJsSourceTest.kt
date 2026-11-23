package app.komikku.sourcejs.mihon

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import app.komikku.sourcejs.JsHostLimits
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.JsSourceException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.preferenceKey
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [MangayomiJsSource] built by [MangayomiJsRuntime] over a small extension, against a real HTTP server
 * ([MockWebServer]) and the desktop compat preference files.
 */
class MangayomiJsSourceTest {

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val dir: File = Files.createTempDirectory("js-source").toFile()
    private val context = Application(dir)
    private val client = OkHttpClient()
    private val runtime = MangayomiJsRuntime(
        preferences = { id -> context.getSharedPreferences(JsSourceIds.preferencesName(id), Context.MODE_PRIVATE) },
        client = { client },
        limits = JsHostLimits(callTimeoutMillis = 20_000),
    )

    private lateinit var base: String

    @BeforeTest
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                return when {
                    path == "/popular" -> json(
                        """[{"name": "One", "link": "/manga/one", "imageUrl": "/covers/one.jpg"},
                            {"name": "No link", "link": ""},
                            {"name": "", "link": "/manga/two", "imageUrl": "https://cdn.test/two.jpg"}]""",
                    )
                    path == "/detail/manga/one" -> json(
                        """{"name": "One (full)", "imageUrl": "//cdn.test/one.jpg", "description": null, "author": "Author",
                            "status": 5, "genre": ["Action", " Drama ", "Action"],
                            "chapters": [{"name": "Chapter 2", "url": "/c/2", "dateUpload": "1700000000000"},
                                         {"name": "", "url": "/c/1", "dateUpload": null, "scanlator": "Team"}]}""",
                    )
                    path == "/detail/manga/done" -> json("""{"name": "", "status": 1, "genre": [], "chapters": []}""")
                    path.startsWith("/img/") -> MockResponse.Builder().body("image ${path.substringAfterLast('/')}").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        base = server.url("/").toString()
    }

    @AfterTest
    fun stop() {
        runtime.close()
        server.close()
        dir.deleteRecursively()
    }

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun descriptor(id: Long = 652112892, baseUrl: String = base) = JsSourceDescriptor(
        id = id,
        name = "Adapter Test",
        lang = "pt-br",
        baseUrl = baseUrl,
        sourceCodeUrl = "https://raw.example.org/adapter.js",
    )

    private fun source(code: String = FULL, descriptor: JsSourceDescriptor = descriptor()) = runBlocking { runtime.source(descriptor, code) }

    private fun prefs(source: MangayomiJsSource): SharedPreferences =
        context.getSharedPreferences(source.preferenceKey(), Context.MODE_PRIVATE)

    @Test
    fun identityFollowsTheIndexEntry() {
        val source = source()
        assertIs<HttpSource>(source)
        assertIs<ConfigurableSource>(source)
        assertEquals(JsSourceIds.komikkuId(652112892), source.id)
        assertEquals("Adapter Test", source.name)
        assertEquals("pt-BR", source.lang)
        assertEquals(base.trimEnd('/'), source.baseUrl)
        // The download directory name cannot equal a Mihon source's ("Adapter Test (PT-BR)").
        assertEquals("Adapter Test [JS] (PT-BR)", source.toString())
        assertEquals("Adapter Test [JS]", source.stubName)
        assertEquals(JsSourceIds.preferencesName(source.id), source.preferenceKey())
        assertSame(prefs(source), source.getSourcePreferences())
        assertEquals(false, source.supportsRelatedMangas)
    }

    @Test
    fun popularMapsTheListAndResolvesCovers() = runBlocking {
        val source = source()
        val page = source.getPopularManga(1)
        assertEquals(true, page.hasNextPage)
        assertEquals(listOf("/manga/one", "/manga/two"), page.mangas.map { it.url })
        assertEquals(listOf("One", "/manga/two"), page.mangas.map { it.title })
        assertEquals(listOf("${base}covers/one.jpg", "https://cdn.test/two.jpg"), page.mangas.map { it.thumbnail_url })
        assertEquals(false, source.getPopularManga(2).hasNextPage)

        // The script's request: its own headers, the declared multi-select default read through SharedPreferences.
        val request = requests.first { it.url.encodedPath == "/popular" }
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("en", request.url.queryParameter("langs"))
        assertEquals(base, request.headers["Referer"])
    }

    @Test
    fun metadataGivesHeadersLatestFiltersAndThePreferenceScreen() {
        val source = source()
        assertEquals(true, source.supportsLatest)
        assertEquals(base, source.headers["Referer"])
        assertEquals("a", source.headers["X-Mode"])
        // Left to OkHttp, which decompresses.
        assertNull(source.headers["Accept-Encoding"])
        assertEquals(listOf("Author", "Type"), source.getFilterList().map { it.name })

        val screen = PreferenceManager(context).apply { sharedPreferencesName = source.preferenceKey() }
            .createPreferenceScreen(context)
        source.setupPreferenceScreen(screen)
        assertEquals(listOf("latest", "mode", "langs"), (0 until screen.preferenceCount).map { screen.getPreference(it).key })
        assertIs<SwitchPreferenceCompat>(screen.getPreference(0))
        assertIs<ListPreference>(screen.getPreference(1))
        assertIs<MultiSelectListPreference>(screen.getPreference(2))
    }

    @Test
    fun aPreferenceChangeRefreshesTheMetadata() = runBlocking {
        val source = source()
        val screen = PreferenceManager(context).apply { sharedPreferencesName = source.preferenceKey() }
            .createPreferenceScreen(context)
        source.setupPreferenceScreen(screen)

        (screen.findPreference<ListPreference>("mode"))!!.value = "b"
        (screen.findPreference<SwitchPreferenceCompat>("latest"))!!.isChecked = false
        (screen.findPreference<MultiSelectListPreference>("langs"))!!.values = setOf("fr", "en")

        // The change listener marked the metadata stale; calls wait for the refresh.
        val metadata = source.awaitMetadata()
        assertEquals("b", metadata.headers["X-Mode"])
        assertEquals("b", source.headers["X-Mode"])
        assertEquals(false, source.supportsLatest)

        source.getPopularManga(1)
        assertEquals("en,fr", requests.last { it.url.encodedPath == "/popular" }.url.queryParameter("langs"))

        // The script's free string store does not count as a preference change.
        prefs(source).edit().putString(SharedPreferencesHostPreferences.STRING_KEY_PREFIX + "token", "t").commit()
        assertSame(metadata, source.awaitMetadata())
    }

    @Test
    fun searchSendsTheFiltersWithTheirStatesOrTheDefaults() = runBlocking {
        val source = source()
        val filters = source.getFilterList()
        (filters[0] as Filter.Text).state = "Oda"
        (filters[1] as Filter.Select<*>).state = 1
        val result = source.getSearchManga(1, "piece", filters)
        assertEquals(listOf("Result piece [\"Oda\",1]"), result.mangas.map { it.title })
        assertEquals("https://cdn.test/r.jpg", result.mangas.single().thumbnail_url)
        // Global search passes an empty list: the source still gets its own list, never [].
        assertEquals(listOf("Result x [\"\",0]"), source.getSearchManga(1, "x", FilterList()).mangas.map { it.title })
    }

    @Test
    fun detailsKeepWhatTheSourceLeavesOut() = runBlocking {
        val source = source()
        val local = SManga.create().apply {
            url = "/manga/one"
            title = "One"
            description = "Stored description"
            artist = "Stored artist"
            genre = "Stored"
            status = SManga.ONGOING
            thumbnail_url = "https://old/cover.jpg"
        }
        val update = source.getMangaUpdate(local, emptyList(), fetchDetails = true, fetchChapters = true)
        with(update.manga) {
            assertEquals("/manga/one", url)
            assertEquals("One (full)", title)
            assertEquals("${base.substringBefore(':')}://cdn.test/one.jpg", thumbnail_url)
            assertEquals("Stored description", description)
            assertEquals("Author", author)
            assertEquals("Stored artist", artist)
            assertEquals("Action, Drama", genre)
            assertEquals(SManga.ONGOING, status)
            assertEquals(true, initialized)
        }
        assertEquals(listOf("/c/2", "/c/1"), update.chapters.map { it.url })
        assertEquals(listOf("Chapter 2", "/c/1"), update.chapters.map { it.name })
        assertEquals(listOf(1_700_000_000_000L, 0L), update.chapters.map { it.date_upload })
        assertEquals(listOf(null, "Team"), update.chapters.map { it.scanlator })
        assertEquals(listOf(-1f, -1f), update.chapters.map { it.chapter_number })

        val done = SManga.create().apply {
            url = "/manga/done"
            title = "Done"
            genre = "Kept"
        }
        val doneUpdate = source.getMangaUpdate(done, emptyList(), fetchDetails = true, fetchChapters = false)
        assertEquals("Done", doneUpdate.manga.title)
        assertEquals("Kept", doneUpdate.manga.genre)
        assertEquals(SManga.COMPLETED, doneUpdate.manga.status)
        assertTrue(doneUpdate.chapters.isEmpty())

        // Chapters only: the manga is passed through.
        val existing = listOf(SChapter.create().apply { url = "/old" })
        val chaptersOnly = source.getMangaUpdate(local, existing, fetchDetails = false, fetchChapters = true)
        assertSame(local, chaptersOnly.manga)
        assertEquals(2, chaptersOnly.chapters.size)
        val detailCalls = requests.count { it.url.encodedPath.startsWith("/detail") }
        val nothing = source.getMangaUpdate(local, existing, fetchDetails = false, fetchChapters = false)
        assertSame(existing, nothing.chapters)
        assertEquals(detailCalls, requests.count { it.url.encodedPath.startsWith("/detail") })
    }

    @Test
    fun pagesCarryTheirHeadersToTheImageRequest() = runBlocking {
        val source = source()
        val pages = source.getPageList(SChapter.create().apply { url = "/c/2" })
        assertEquals(listOf("${base}img/1.jpg", "${base}img/2.jpg"), pages.map { it.imageUrl })
        assertEquals(listOf(0, 1), pages.map { it.index })
        assertEquals(JsPageRef("${base}img/2.jpg", mapOf("X-Page" to "2", "Referer" to "https://page.test/")), JsPageRef.decode(pages[1].url))

        val image = source.getImage(pages[1]).use { it.body.string() }
        assertEquals("image 2.jpg", image)
        val request = requests.last { it.url.encodedPath == "/img/2.jpg" }
        assertEquals("2", request.headers["X-Page"])
        assertEquals("https://page.test/", request.headers["Referer"])
        assertEquals("a", request.headers["X-Mode"])

        // A cleared image URL (reader retry) is recovered from Page.url; the data saver's replacement is used as is.
        pages[0].imageUrl = null
        assertEquals("${base}img/1.jpg", source.getImageUrl(pages[0]))
        pages[0].imageUrl = "${base}img/saved.jpg"
        assertEquals("image saved.jpg", source.getImage(pages[0]).use { it.body.string() })
        assertEquals(base, requests.last { it.url.encodedPath == "/img/saved.jpg" }.headers["Referer"])
    }

    @Test
    fun webUrlsUseTheLinkOrTheHomePage() {
        val source = source()
        assertEquals("${base}manga/one", source.getMangaUrl(SManga.create().apply { url = "/manga/one" }))
        assertEquals("https://other.test/c", source.getChapterUrl(SChapter.create().apply { url = "https://other.test/c" }))
        assertEquals(base.trimEnd('/'), source.getMangaUrl(SManga.create().apply { url = "opaque-id" }))
    }

    @Test
    fun missingLatestAndSearchGiveEmptyPagesAndOtherFailuresReadableErrors() = runBlocking {
        val minimal = source(MINIMAL, descriptor(id = 7, baseUrl = "http://127.0.0.1:1"))
        assertEquals(true, minimal.supportsLatest)
        val latest = minimal.getLatestUpdates(1)
        assertTrue(latest.mangas.isEmpty())
        assertEquals(false, latest.hasNextPage)
        assertEquals(false, minimal.supportsLatest)
        assertTrue(minimal.getSearchManga(1, "q", FilterList()).mangas.isEmpty())
        assertTrue(minimal.getFilterList().isEmpty())

        // A network failure the script does not handle is the original IOException.
        assertFailsWith<IOException> { minimal.getPopularManga(1) }

        val notImplemented = assertFailsWith<JsSourceException.NotImplemented> {
            minimal.getPageList(SChapter.create().apply { url = "/c" })
        }
        assertEquals("Extension \"Adapter Test\" failed while getPageList: getPageList not implemented", notImplemented.message)

        val full = source()
        val error = assertFailsWith<JsSourceException.ScriptError> { full.getLatestUpdates(1) }
        assertEquals("Extension \"Adapter Test\" failed while getLatestUpdates(1): Error: the site changed", error.message)
        assertEquals(true, full.supportsLatest)
    }

    @Test
    fun aRebuiltSourceReplacesTheOldOne() = runBlocking {
        val first = source()
        val second = source()
        // The first one no longer listens to preference changes.
        prefs(second).edit().putString("mode", "b").commit()
        assertEquals("b", second.awaitMetadata().headers["X-Mode"])
        assertEquals("a", first.awaitMetadata().headers["X-Mode"])
    }

    @Test
    fun onlyTheMissingMethodItselfCountsAsNoLatestOrNoSearch() = runBlocking {
        val source = source(DELEGATING, descriptor(id = 10))
        val latest = assertFailsWith<JsSourceException.NotImplemented> { source.getLatestUpdates(1) }
        assertEquals("getDetail", latest.member)
        assertEquals(true, source.supportsLatest)
        val search = assertFailsWith<JsSourceException.NotImplemented> { source.getSearchManga(1, "q", FilterList()) }
        assertEquals("getPageList", search.member)
    }

    companion object {
        /** Popular, latest (failing), search, details, pages, filters, preferences and headers. */
        val FULL = """
            const mangayomiSources = [{ "name": "Adapter Test", "lang": "pt-br", "id": 652112892 }];
            class DefaultExtension extends MProvider {
              base() { return this.source.baseUrl.replace(/\/+$/, ""); }
              get supportsLatest() { return new SharedPreferences().get("latest"); }
              getHeaders(url) {
                return { "Referer": this.base() + "/", "X-Mode": new SharedPreferences().get("mode"), "Accept-Encoding": "identity" };
              }
              async getPopular(page) {
                const langs = new SharedPreferences().get("langs").join(",");
                const res = await new Client().get(this.base() + "/popular?page=" + page + "&langs=" + langs, { "Referer": this.base() + "/" });
                return { list: JSON.parse(res.body), hasNextPage: page < 2 };
              }
              async getLatestUpdates(page) { throw new Error("the site changed"); }
              async search(query, page, filters) {
                return { list: [{ name: "Result " + query + " " + JSON.stringify(filters.map(f => f.state)), link: "/manga/result", imageUrl: "https://cdn.test/r.jpg" }], hasNextPage: false };
              }
              async getDetail(url) {
                const res = await new Client().get(this.base() + "/detail" + url);
                return JSON.parse(res.body);
              }
              async getPageList(url) {
                return [this.base() + "/img/1.jpg", { url: "/img/2.jpg", headers: { "X-Page": "2", "Referer": "https://page.test/" } }, "", this.base() + "/img/1.jpg"];
              }
              getFilterList() {
                return [
                  { type_name: "TextFilter", name: "Author", state: "" },
                  { type_name: "SelectFilter", name: "Type", state: 0, values: [
                    { type_name: "SelectOption", name: "All", value: "" },
                    { type_name: "SelectOption", name: "Manga", value: "manga" }] }
                ];
              }
              getSourcePreferences() {
                return [
                  { key: "latest", switchPreferenceCompat: { title: "Latest", summary: "", value: true } },
                  { key: "mode", listPreference: { title: "Mode", summary: "", valueIndex: 0, entries: ["A", "B"], entryValues: ["a", "b"] } },
                  { key: "langs", multiSelectListPreference: { title: "Languages", summary: "", entries: ["English", "French"], entryValues: ["en", "fr"], values: ["en"] } }
                ];
              }
            }
        """.trimIndent()

        /** Latest and search that call other, missing, methods. */
        val DELEGATING = """
            const mangayomiSources = [{ "name": "Adapter Test", "lang": "pt-br", "id": 10 }];
            class DefaultExtension extends MProvider {
              async getLatestUpdates(page) { return await this.getDetail("x"); }
              async search(query, page, filters) { return await this.getPageList("y"); }
            }
        """.trimIndent()

        /** Only getPopular, against the source's (unreachable) base URL. */
        val MINIMAL = """
            const mangayomiSources = [{ "name": "Adapter Test", "lang": "pt-br", "id": 7 }];
            class DefaultExtension extends MProvider {
              async getPopular(page) {
                const res = await new Client().get(this.source.baseUrl + "/popular");
                return { list: [], hasNextPage: false };
              }
            }
        """.trimIndent()
    }
}
