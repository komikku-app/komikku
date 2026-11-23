package app.komikku.desktop.jssource

import android.app.Application
import app.komikku.desktop.compat.DesktopPreferenceStores
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.jssource.JsSourcePreferences
import app.komikku.shared.jssource.JsSourceStoreException
import app.komikku.shared.jssource.JsSourceStoreException.Reason
import app.komikku.shared.jssource.MangayomiSourceStore
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.repo.JsSourceStatus
import app.komikku.sourcejs.sha256Hex
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The JavaScript source store end to end against [MockWebServer] in a temporary directory: add a repository → refresh →
 * install (confirmed SHA-256) → the source appears and browses → a changed text under the same version → update → a new
 * version → reload from disk → NSFW hidden → uninstall; plus refused `http` URLs, a SHA-256 mismatch, size limits,
 * a broken index, a corrupt manifest and a text that does not load.
 *
 * `MockWebServer` serves plain HTTP, so the store under test may fetch from the server's host; the default policy
 * (`https` only) is checked against the same server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MangayomiSourceStoreTest {

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, MockResponse>()

    private lateinit var app: Application
    private lateinit var dataDir: File
    private lateinit var storeDir: File
    private lateinit var store: MangayomiSourceStore
    private val showNsfw = MutableStateFlow(true)
    private val stores = mutableListOf<MangayomiSourceStore>()

    @BeforeAll
    fun setUp(@TempDir dir: File) {
        dataDir = File(dir, "data")
        storeDir = File(dir, "store")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.url.encodedPath] ?: MockResponse.Builder().code(404).build()
        }
        server.start()
        app = DesktopInjekt.init(dataDir)
        store = newStore(storeDir)

        route("/repo/index.json", index(version = "0.0.1"))
        route("/repo/repo.json", """{"name":"Fixture repo","website":"https://example.org"}""")
        route("/code/source.js", code("v1"))
        route("/icon.png", "PNG-BYTES")
        route("/site/list", """<html><body><div class="item"><a href="/m/1">First v1</a></div></body></html>""")
    }

    @AfterAll
    fun tearDown() {
        stores.forEach { it.close() }
        server.close()
    }

    private fun newStore(
        dir: File,
        allowUrl: (HttpUrl) -> Boolean = { it.isHttps || it.host == server.hostName },
        limits: MangayomiSourceStore.Limits = MangayomiSourceStore.Limits(),
        preferences: JsSourcePreferences = JsSourcePreferences(Injekt.get<PreferenceStore>()),
    ): MangayomiSourceStore = MangayomiSourceStore(
        directory = dir,
        preferences = preferences,
        runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>()),
        client = { Injekt.get<NetworkHelper>().client },
        showNsfw = showNsfw,
        allowUrl = allowUrl,
        limits = limits,
    ).also { stores += it }

    private fun route(path: String, body: String, code: Int = 200) {
        routes[path] = MockResponse.Builder().code(code).body(Buffer().writeUtf8(body)).build()
    }

    private fun url(path: String) = server.url(path).toString()

    private fun index(version: String, nsfw: Boolean = false, codePath: String = "/code/source.js") = """
        [
          {"name":"Fixture JS","id":$ID,"baseUrl":"${url("/site")}","lang":"en","typeSource":"single",
           "iconUrl":"${url("/icon.png")}","dateFormat":"","dateFormatLocale":"","isNsfw":$nsfw,"hasCloudflare":false,
           "sourceCodeUrl":"${url(codePath)}","apiUrl":"","version":"$version","isManga":true,"itemType":0,
           "isFullData":false,"appMinVerReq":"0.5.0","additionalParams":"","sourceCodeLanguage":1,
           "notes":"Fixture notes"},
          {"name":"Anime","id":99,"sourceCodeUrl":"${url("/code/anime.js")}","itemType":1,"sourceCodeLanguage":1}
        ]
    """.trimIndent()

    /** A small extension whose popular list is read from the fixture site; [tag] changes the text. */
    private fun code(tag: String) = """
        const mangayomiSources = [{ "name": "Fixture JS", "lang": "en", "id": $ID }];
        // $tag
        class DefaultExtension extends MProvider {
          get supportsLatest() { return false; }
          async getPopular(page) {
            const res = await new Client().get(this.source.baseUrl + "/list");
            const items = new Document(res.body).select("div.item a").map((a) => ({ name: a.text, link: a.attr("href") }));
            return { list: items, hasNextPage: false };
          }
          async getDetail(url) { return { name: "x", chapters: [] }; }
          async getPageList(url) { return []; }
          getFilterList() { return []; }
          getSourcePreferences() { return [{ key: "k", checkBoxPreference: { title: "K", summary: "", value: true } }]; }
        }
    """.trimIndent()

    private suspend fun item(s: MangayomiSourceStore = store) = s.items.first().single { it.id == ID }

    @Test
    @Order(1)
    fun `refuses http and broken repositories`(): Unit = runBlocking {
        withTimeout(10_000) { store.isLoaded.first { it } }
        store.installed.value.shouldNotBeNull().shouldBeEmpty()

        shouldThrow<JsSourceStoreException> { store.addRepository("http://example.org/index.json") }.reason shouldBe Reason.INSECURE_URL
        shouldThrow<JsSourceStoreException> { store.addRepository("not a url") }.reason shouldBe Reason.INSECURE_URL
        // The default policy refuses the (plain HTTP) fixture server itself.
        val httpsOnly = newStore(File(storeDir.parentFile, "https-only"), allowUrl = { it.isHttps })
        shouldThrow<JsSourceStoreException> { httpsOnly.addRepository(url("/repo/index.json")) }.reason shouldBe Reason.INSECURE_URL

        // A redirect to a URL the policy refuses is not followed.
        routes["/moved/index.json"] = MockResponse.Builder().code(302).addHeader("Location", "http://example.invalid/index.json").build()
        shouldThrow<JsSourceStoreException> { store.addRepository(url("/moved/index.json")) }.reason shouldBe Reason.INSECURE_URL
        routes["/loop/index.json"] = MockResponse.Builder().code(302).addHeader("Location", url("/loop/index.json")).build()
        shouldThrow<JsSourceStoreException> { store.addRepository(url("/loop/index.json")) }.reason shouldBe Reason.DOWNLOAD_FAILED

        route("/broken/index.json", """{"meta":{"name":"v2 store"}}""")
        shouldThrow<JsSourceStoreException> { store.addRepository(url("/broken/index.json")) }.reason shouldBe Reason.INVALID_INDEX
        shouldThrow<JsSourceStoreException> { store.addRepository(url("/missing/index.json")) }.reason shouldBe Reason.DOWNLOAD_FAILED
        store.repositories.first().shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `adds a repository and lists its JavaScript manga entries`(): Unit = runBlocking {
        store.addRepository(" ${url("/repo/index.json")} ") shouldBe url("/repo/index.json")
        Injekt.get<PreferenceStore>().getStringSet(JsSourcePreferences.REPOSITORIES_KEY).get() shouldBe setOf(url("/repo/index.json"))

        val repo = store.repositories.first().single()
        repo.name shouldBe "Fixture repo"
        repo.website shouldBe "https://example.org"
        repo.entries shouldBe 1
        store.available.first().map { it.id } shouldContainExactly listOf(ID)
        item().installed.shouldBeNull()
        item().status.shouldBeNull()

        store.refresh()
        store.available.first().single().descriptor.notes shouldBe "Fixture notes"
    }

    @Test
    @Order(3)
    fun `installs a confirmed text and the source browses`(): Unit = runBlocking {
        val offer = store.offer(ID).shouldNotBeNull()
        val download = store.download(offer)
        download.sha256 shouldBe sha256Hex(code("v1"))
        download.host shouldBe server.hostName
        download.isUpdate shouldBe false

        val source = store.install(offer, download.sha256)
        source.id shouldBe JsSourceIds.komikkuId(ID)
        store.sources.first { it.isNotEmpty() }.single() shouldBe source
        source.getPopularManga(1).mangas.single().title shouldBe "First v1"

        val installed = store.installed.value.shouldNotBeNull().single()
        installed.sha256 shouldBe download.sha256
        installed.repoUrl shouldBe url("/repo/index.json")
        installed.metadata.shouldNotBeNull().preferences.single().key shouldBe "k"
        File(storeDir, "code/${download.sha256}.js").readText() shouldBe code("v1")
        File(storeDir, "manifest.json").readText() shouldContain download.sha256
        store.iconFileForSource(source.id).shouldNotBeNull().readText() shouldBe "PNG-BYTES"
        store.installedSource(source.id).shouldNotBeNull().descriptor.notes shouldBe "Fixture notes"
        item().status shouldBe JsSourceStatus.UP_TO_DATE
    }

    @Test
    @Order(4)
    fun `a changed text under the same version needs a confirmed update`(): Unit = runBlocking {
        val oldSha = store.installed.value!!.single().sha256
        route("/code/source.js", code("v1, changed"))
        store.refresh()
        item().status shouldBe JsSourceStatus.TEXT_CHANGED

        val download = store.downloadUpdate(ID)
        download.isUpdate shouldBe true
        download.textChangedUnderSameVersion shouldBe true
        download.installed!!.sha256 shouldBe oldSha

        // The server changes the text again between confirmation and install: refused, nothing changes.
        route("/code/source.js", code("v1, changed again"))
        shouldThrow<JsSourceStoreException> { store.update(ID, download.sha256) }.reason shouldBe Reason.SHA_MISMATCH
        store.installed.value!!.single().sha256 shouldBe oldSha

        val again = store.downloadUpdate(ID)
        store.update(ID, again.sha256)
        store.installed.value!!.single().sha256 shouldBe sha256Hex(code("v1, changed again"))
        item().status shouldBe JsSourceStatus.UP_TO_DATE
        File(storeDir, "code/$oldSha.js").exists() shouldBe false
    }

    @Test
    @Order(5)
    fun `a new version is an update`(): Unit = runBlocking {
        route("/repo/index.json", index(version = "0.0.2"))
        route("/code/source.js", code("v2"))
        route("/site/list", """<html><body><div class="item"><a href="/m/1">First v2</a></div></body></html>""")
        store.refresh()
        item().status shouldBe JsSourceStatus.UPDATE_AVAILABLE
        item().hasUpdate shouldBe true

        val download = store.downloadUpdate(ID)
        download.textChangedUnderSameVersion shouldBe false
        val installedAt = store.installed.value!!.single().installedAt
        val source = store.update(ID, download.sha256)
        source.getPopularManga(1).mangas.single().title shouldBe "First v2"
        store.sources.value.single() shouldBe source
        val record = store.installed.value!!.single()
        record.descriptor.version shouldBe "0.0.2"
        record.installedAt shouldBe installedAt
        item().status shouldBe JsSourceStatus.UP_TO_DATE
    }

    @Test
    @Order(6)
    fun `a reloaded store builds its sources from the files`(): Unit = runBlocking {
        val reloaded = newStore(storeDir)
        withTimeout(20_000) { reloaded.isLoaded.first { it } }
        val source = withTimeout(5_000) { reloaded.sources.first { it.isNotEmpty() } }.single()
        source.id shouldBe JsSourceIds.komikkuId(ID)
        source.getPopularManga(1).mangas.single().title shouldBe "First v2"
        // Not read yet in this store: no repository listing, so not obsolete either.
        item(reloaded).status shouldBe JsSourceStatus.UP_TO_DATE
        reloaded.close()
    }

    @Test
    @Order(7)
    fun `NSFW sources are left out while hidden`(): Unit = runBlocking {
        route("/repo/index.json", index(version = "0.0.3", nsfw = true))
        store.refresh()
        store.update(ID, store.downloadUpdate(ID).sha256)
        store.sources.value.size shouldBe 1
        showNsfw.value = false
        withTimeout(5_000) { store.sources.first { it.isEmpty() } }
        // Still listed (installed) so it can be uninstalled.
        item().installed.shouldNotBeNull()
        showNsfw.value = true
        withTimeout(5_000) { store.sources.first { it.isNotEmpty() } }
    }

    @Test
    @Order(8)
    fun `a source no repository offers is obsolete`(): Unit = runBlocking {
        route("/repo/index.json", "[]")
        store.refresh()
        item().status shouldBe JsSourceStatus.OBSOLETE
        route("/repo/index.json", index(version = "0.0.3", nsfw = true))
        store.refresh()
        item().status shouldBe JsSourceStatus.UP_TO_DATE
    }

    @Test
    @Order(9)
    fun `uninstall removes the source and its files`(): Unit = runBlocking {
        val sha = store.installed.value!!.single().sha256
        val sourceId = JsSourceIds.komikkuId(ID)
        store.uninstall(ID)
        store.sources.value.shouldBeEmpty()
        store.installed.value.shouldNotBeNull().shouldBeEmpty()
        File(storeDir, "code/$sha.js").exists() shouldBe false
        store.iconFileForSource(sourceId).shouldBeNull()
        File(storeDir, "icons/$ID").exists() shouldBe false
        item().installed.shouldBeNull()

        store.removeRepository(url("/repo/index.json"))
        store.repositories.first().shouldBeEmpty()
        store.available.first().shouldBeEmpty()
    }

    @Test
    @Order(10)
    fun `files above the limits are refused`(): Unit = runBlocking {
        val small = newStore(
            File(storeDir.parentFile, "small"),
            limits = MangayomiSourceStore.Limits(codeBytes = 64),
            preferences = JsSourcePreferences(DesktopPreferenceStores.get("small", File(storeDir.parentFile, "small-preferences"))),
        )
        withTimeout(10_000) { small.isLoaded.first { it } }
        small.addRepository(url("/repo/index.json"))
        shouldThrow<JsSourceStoreException> { small.download(small.offer(ID)!!) }.reason shouldBe Reason.TOO_LARGE
    }

    @Test
    @Order(11)
    fun `a corrupt manifest starts empty and is set aside`(): Unit = runBlocking {
        val dir = File(storeDir.parentFile, "corrupt").apply { mkdirs() }
        File(dir, "manifest.json").writeText("{ not json")
        val corrupt = newStore(dir)
        withTimeout(10_000) { corrupt.isLoaded.first { it } }
        corrupt.installed.value.shouldNotBeNull().shouldBeEmpty()
        corrupt.sources.value.shouldBeEmpty()
        dir.listFiles()!!.map { it.name }.single { it.startsWith("manifest.json.corrupt-") }
    }

    @Test
    @Order(12)
    fun `a text that does not load is reported and the others still load`(): Unit = runBlocking {
        // Install two entries, then damage one text file.
        route("/repo2/index.json", index(version = "1.0.0", codePath = "/code/good.js").replace("$ID", "$OTHER_ID"))
        route("/code/good.js", code("good").replace("$ID", "$OTHER_ID"))
        route("/repo/index.json", index(version = "0.0.3"))
        route("/code/source.js", code("v3"))
        store.addRepository(url("/repo/index.json"))
        store.addRepository(url("/repo2/index.json"))
        store.install(store.offer(ID)!!, sha256Hex(code("v3")))
        store.install(store.offer(OTHER_ID)!!, sha256Hex(code("good").replace("$ID", "$OTHER_ID")))
        File(storeDir, "code/${sha256Hex(code("v3"))}.js").writeText("tampered")

        val reloaded = newStore(storeDir)
        withTimeout(20_000) { reloaded.isLoaded.first { it } }
        withTimeout(5_000) { reloaded.sources.first { it.isNotEmpty() } }.map { it.id } shouldContainExactly listOf(JsSourceIds.komikkuId(OTHER_ID))
        reloaded.items.first().single { it.id == ID }.loadError.shouldNotBeNull() shouldContain "SHA-256"

        // A store asked to install while it is still loading waits for its files first.
        val busy = newStore(storeDir)
        busy.addRepository(url("/repo2/index.json"))
        busy.install(busy.offer(OTHER_ID)!!, sha256Hex(code("good").replace("$ID", "$OTHER_ID")))
        busy.installed.value!!.map { it.id }.toSet() shouldBe setOf(ID, OTHER_ID)
        busy.close()

        // Broken JavaScript is refused at install.
        route("/code/source.js", "this is not javascript (")
        shouldThrow<JsSourceStoreException> {
            store.install(store.offer(ID)!!, sha256Hex("this is not javascript ("))
        }.reason shouldBe Reason.LOAD_FAILED
        reloaded.close()
    }

    companion object {
        private const val ID = 652112892L
        private const val OTHER_ID = 777L
    }
}
