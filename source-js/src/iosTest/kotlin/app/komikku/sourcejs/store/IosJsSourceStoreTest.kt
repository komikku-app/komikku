package app.komikku.sourcejs.store

import app.komikku.sourcejs.IosJsSourceRuntime
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.repo.JsSourceStatus
import app.komikku.sourcejs.sha256Hex
import app.komikku.sourcejs.store.JsSourceStoreException.Reason
import app.komikku.sourcejs.timedTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [IosJsSourceStore] in a temporary directory with a [FakeHostHttp]: a repository read through to an installed source
 * that browses, updates (a new version, a changed text under the same version), failed updates that keep the
 * installed version working, hash and size checks, insecure URLs and redirects, uninstall (files and preferences), a
 * reload from the cached metadata without running the script, and corrupt cache files. No network.
 */
@OptIn(ExperimentalForeignApi::class)
class IosJsSourceStoreTest {

    private val tag = Random.nextLong().toULong().toString(16)
    private val root = "${NSTemporaryDirectory().trimEnd('/')}/iosjsstore-$tag"
    private val repositorySuite = "app.komikku.sourcejs.test.repos.$tag"
    private val preferenceSuitePrefix = "app.komikku.sourcejs.test.store.$tag."
    private val repositoryDefaults = NSUserDefaults(suiteName = repositorySuite)

    private val http = FakeHostHttp()
    private val showNsfw = MutableStateFlow(true)
    private val storeLog = MutableStateFlow<List<String>>(emptyList())
    private val scriptLog = MutableStateFlow<List<String>>(emptyList())
    private val stores = mutableListOf<IosJsSourceStore>()
    private val usedIds = mutableSetOf(ID, OTHER_ID)

    private fun newStore(
        limits: IosJsSourceStore.Limits = IosJsSourceStore.Limits(),
        clock: () -> Long = { 1_000L },
    ): IosJsSourceStore = IosJsSourceStore(
        root = root,
        http = http,
        repositoryDefaults = repositoryDefaults,
        runtime = { declared ->
            IosJsSourceRuntime(
                http = http,
                suiteName = { preferenceSuitePrefix + it },
                declaredPreferences = declared,
                log = HostLog { _, source, message -> scriptLog.update { it + "${source.id}: $message" } },
            )
        },
        showNsfw = showNsfw,
        limits = limits,
        clock = clock,
        log = { line -> storeLog.update { it + line } },
    ).also { stores += it }

    @AfterTest
    fun cleanUp() {
        stores.forEach { it.close() }
        NSFileManager.defaultManager.removeItemAtPath(root, null)
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(repositorySuite)
        usedIds.forEach {
            NSUserDefaults.standardUserDefaults.removePersistentDomainForName(preferenceSuitePrefix + JsSourceIds.komikkuId(it))
        }
    }

    // ---- Fixtures ----

    private fun index(
        version: String,
        id: Long = ID,
        codeUrl: String = CODE_URL,
        nsfw: Boolean = false,
        name: String = "Fixture JS",
    ) = """
        [
          {"name":"$name","id":$id,"baseUrl":"$SITE","lang":"en","typeSource":"single","iconUrl":"$ICON_URL",
           "dateFormat":"","dateFormatLocale":"","isNsfw":$nsfw,"hasCloudflare":false,"sourceCodeUrl":"$codeUrl",
           "apiUrl":"","version":"$version","isManga":true,"itemType":0,"isFullData":false,"appMinVerReq":"0.5.0",
           "additionalParams":"","sourceCodeLanguage":1,"notes":"Fixture notes"},
          {"name":"Anime","id":99,"sourceCodeUrl":"https://repo.test/code/anime.js","itemType":1,"sourceCodeLanguage":1},
          {"name":"Dart","id":98,"sourceCodeUrl":"https://repo.test/code/dart.dart","itemType":0,"sourceCodeLanguage":0}
        ]
    """.trimIndent()

    /**
     * A small extension: its popular list is read from the fixture site (`/list`, with [tag] in each name),
     * `supportsLatest` is its "k" preference, and reading its preferences logs a line.
     */
    private fun code(tag: String, id: Long = ID) = """
        const mangayomiSources = [{ "name": "Fixture JS", "lang": "en", "id": $id }];
        class DefaultExtension extends MProvider {
          get supportsLatest() { return new SharedPreferences().get("k"); }
          async getPopular(page) {
            const res = await new Client().get(this.source.baseUrl + "/list");
            const items = new Document(res.body).select("div.item a").map((a) => ({ name: a.text + " $tag", link: a.attr("href") }));
            return { list: items, hasNextPage: false };
          }
          async getDetail(url) { return { name: "x", chapters: [] }; }
          async getPageList(url) { return []; }
          getFilterList() { return []; }
          getSourcePreferences() {
            console.log("preferences read");
            return [
              { key: "k", checkBoxPreference: { title: "K", summary: "", value: true } },
              { key: "ua", editTextPreference: { title: "UA", summary: "", value: "Fixture/1", dialogTitle: "", dialogMessage: "", text: "" } },
            ];
          }
        }
    """.trimIndent()

    private fun serveRepository(version: String = "1.0.0", text: String = code("v1")) {
        http.route(REPO_URL, index(version))
        http.route(REPO_INFO_URL, """{"name":"Fixture repo","website":"https://example.org"}""")
        http.route(CODE_URL, text)
        http.route(ICON_URL, "PNG-BYTES")
        http.route("$SITE/list", """<html><body><div class="item"><a href="/m/1">First</a></div></body></html>""")
    }

    private suspend fun IosJsSourceStore.loaded() = apply { withTimeout(10_000) { isLoaded.first { it } } }

    private suspend fun IosJsSourceStore.item(id: Long = ID) = items.first().single { it.id == id }

    private suspend fun IosJsSourceStore.installFixture(text: String = code("v1")): IosJsSource {
        addRepository(REPO_URL)
        val download = download(offer(ID)!!)
        return install(download.offer, download.sha256)
    }

    private fun file(path: String) = StoreFiles.readBytes("$root/$path")

    private fun manifest(): List<InstalledJsSource> =
        json.decodeFromString(JsSourceManifest.serializer(), file("manifest.json")!!.decodeToString()).sources

    private fun descriptor(id: Long = ID) = JsSourceDescriptor(id = id, name = "Fixture JS")

    private fun suite(id: Long = ID) = NSUserDefaults(suiteName = preferenceSuitePrefix + JsSourceIds.komikkuId(id))

    private inline fun assertStoreFailure(reason: Reason, block: () -> Unit): JsSourceStoreException {
        val e = assertFailsWith<JsSourceStoreException> { block() }
        assertEquals(reason, e.reason, e.message)
        return e
    }

    // ---- Tests ----

    @Test
    fun indexIsReadThroughToAnInstalledSource() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        assertEquals(emptyList(), store.installed.value)

        assertEquals(REPO_URL, store.addRepository("  $REPO_URL "))
        val repository = store.repositories.first().single()
        assertEquals("Fixture repo", repository.name)
        assertEquals("https://example.org", repository.website)
        // Only the JavaScript manga entry is offered.
        assertEquals(1, repository.entries)
        assertEquals(listOf(REPO_URL), storedRepositories())
        val offered = store.item()
        assertNull(offered.installed)
        assertNull(offered.status)
        assertEquals("1.0.0", offered.offer!!.descriptor.version)

        val text = code("v1")
        val download = store.download(offered.offer)
        assertEquals("repo.test", download.host)
        assertFalse(download.redirected)
        assertFalse(download.isUpdate)
        assertEquals(sha256Hex(text), download.sha256)
        assertEquals(text.encodeToByteArray().size.toLong(), download.sizeBytes)

        val source = store.install(download.offer, download.sha256)
        assertEquals(JsSourceIds.komikkuId(ID), source.sourceId)
        assertSame(source, store.sources.first { it.isNotEmpty() }.single())
        assertSame(source, store.source(source.sourceId))
        // The store's downloads ask the client not to follow redirects and to cap the body.
        assertTrue(http.requests.filter { it.url == CODE_URL }.all { !it.followRedirects && it.maxBodyBytes == 4L * 1024 * 1024 })

        // Files: the manifest with the metadata, the text named by its SHA-256, the icon.
        val record = manifest().single()
        assertEquals(download.sha256, record.sha256)
        assertEquals(REPO_URL, record.repoUrl)
        assertEquals(1_000L, record.installedAt)
        assertEquals(listOf("k", "ua"), record.metadata!!.preferences.map { it.key })
        assertContentEquals(text.encodeToByteArray(), file("code/${download.sha256}.js"))
        assertEquals("$root/icons/$ID", store.iconFileForSource(source.sourceId))
        assertContentEquals("PNG-BYTES".encodeToByteArray(), file("icons/$ID"))
        // Atomic writes leave no temporary files.
        assertEquals(emptyList(), filesUnder(root).filter { it.endsWith(".tmp") })

        // The runtime types the preferences from the cached metadata.
        assertEquals(record.metadata.preferences, store.declaredPreferences(descriptor()))
        val item = store.item()
        assertEquals(JsSourceStatus.UP_TO_DATE, item.status)
        assertFalse(item.hasUpdate)
    }

    @Test
    fun installedSourceRunsGetPopularThroughTheRuntime() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        val source = store.installFixture()

        val page = source.extension.getPopular(1)
        assertEquals(listOf("First v1" to "/m/1"), page.list.map { it.name to it.link })
        assertTrue(http.urls.contains("$SITE/list"))
        // The script's preferences live in its own suite, typed by the declarations the store cached.
        assertEquals(true, source.metadata!!.supportsLatest)
        val preferences = store.runtime.preferences(source.descriptor)
        preferences.setValue("k", JsPreferenceValue.Bool(false))
        assertEquals(false, store.refreshMetadata(ID).supportsLatest)
        assertEquals(false, manifest().single().metadata!!.supportsLatest)
        // A value of the wrong type for its declaration reads as missing (the script gets the default).
        suite().setObject("not a boolean", forKey = "k")
        assertNull(preferences.getValue("k"))
        assertEquals(true, source.extension.supportsLatest())
    }

    @Test
    fun aNewVersionIsAnUpdate() = timedTest {
        serveRepository()
        var now = 1_000L
        val store = newStore(clock = { now }).loaded()
        val old = store.installFixture()
        val oldSha = manifest().single().sha256

        val v2 = code("v2")
        http.route(REPO_URL, index(version = "1.0.1"))
        http.route(CODE_URL, v2)
        store.refresh()
        val item = store.item()
        assertEquals(JsSourceStatus.UPDATE_AVAILABLE, item.status)
        assertTrue(item.hasUpdate)

        now = 2_000L
        val download = store.downloadUpdate(ID)
        assertTrue(download.isUpdate)
        assertFalse(download.textChangedUnderSameVersion)
        val updated = store.update(ID, download.sha256)
        assertSame(updated, store.sources.first { it.singleOrNull() !== old }.single())

        val record = manifest().single()
        assertEquals("1.0.1", record.descriptor.version)
        assertEquals(1_000L, record.installedAt)
        assertEquals(2_000L, record.updatedAt)
        assertNull(file("code/$oldSha.js"))
        assertNotNull(file("code/${sha256Hex(v2)}.js"))
        assertEquals("First v2", updated.extension.getPopular(1).list.single().name)
        assertEquals(JsSourceStatus.UP_TO_DATE, store.item().status)
    }

    @Test
    fun aChangedTextUnderTheSameVersionShowsAndUpdates() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        store.installFixture()

        val changed = code("changed")
        http.route(CODE_URL, changed)
        store.refresh()
        assertEquals(JsSourceStatus.TEXT_CHANGED, store.item().status)
        val download = store.downloadUpdate(ID)
        assertTrue(download.textChangedUnderSameVersion)
        store.update(ID, download.sha256)
        assertEquals(sha256Hex(changed), manifest().single().sha256)
        assertEquals(JsSourceStatus.UP_TO_DATE, store.item().status)
    }

    @Test
    fun aFailedUpdateKeepsTheInstalledVersionWorking() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        val old = store.installFixture()
        val oldSha = manifest().single().sha256
        val oldManifest = file("manifest.json")

        // A new version whose text does not load.
        val broken = "class DefaultExtension extends MProvider { getPopular( {"
        http.route(REPO_URL, index(version = "1.0.1"))
        http.route(CODE_URL, broken)
        store.refresh()
        assertStoreFailure(Reason.LOAD_FAILED) { store.update(ID, sha256Hex(broken)) }
        assertNull(file("code/${sha256Hex(broken)}.js"))

        // A download that fails.
        http.fail(CODE_URL)
        assertStoreFailure(Reason.DOWNLOAD_FAILED) { store.downloadUpdate(ID) }
        http.route(CODE_URL, "", status = 500)
        assertStoreFailure(Reason.DOWNLOAD_FAILED) { store.downloadUpdate(ID) }

        // A manifest that cannot be written: a non-empty directory stands in its place.
        val v2 = code("v2")
        http.route(CODE_URL, v2)
        val download = store.downloadUpdate(ID)
        NSFileManager.defaultManager.removeItemAtPath("$root/manifest.json", null)
        StoreFiles.writeAtomically("$root/manifest.json/blocker", "x".encodeToByteArray())
        assertStoreFailure(Reason.STORAGE_FAILED) { store.update(ID, download.sha256) }
        assertNull(file("code/${download.sha256}.js"))

        // Each time the installed version is unchanged and still works.
        assertSame(old, store.sources.value.single())
        assertEquals("1.0.0", store.installed.value!!.single().descriptor.version)
        assertNotNull(file("code/$oldSha.js"))
        assertNotNull(store.iconFileForSource(old.sourceId))
        assertEquals("First v1", old.extension.getPopular(1).list.single().name)
        assertEquals(JsSourceStatus.UPDATE_AVAILABLE, store.item().status)

        // Uninstalling fails the same way and keeps the source and its preferences.
        store.runtime.preferences(old.descriptor).setValue("k", JsPreferenceValue.Bool(false))
        assertStoreFailure(Reason.STORAGE_FAILED) { store.uninstall(ID) }
        assertSame(old, store.sources.value.single())
        assertNotNull(suite().objectForKey("k"))

        // Once the manifest can be written again, the update goes through.
        NSFileManager.defaultManager.removeItemAtPath("$root/manifest.json", null)
        StoreFiles.writeAtomically("$root/manifest.json", oldManifest!!)
        val updated = store.update(ID, download.sha256)
        assertEquals("1.0.1", manifest().single().descriptor.version)
        assertNull(file("code/$oldSha.js"))
        assertEquals("First v2", updated.extension.getPopular(1).list.single().name)
    }

    @Test
    fun hashAndSizeAreChecked() = timedTest {
        serveRepository()
        val store = newStore(limits = IosJsSourceStore.Limits(codeBytes = 2_000)).loaded()
        store.addRepository(REPO_URL)
        val offer = store.offer(ID)!!

        // The text changed since it was confirmed.
        val download = store.download(offer)
        http.route(CODE_URL, code("other"))
        assertStoreFailure(Reason.SHA_MISMATCH) { store.install(offer, download.sha256) }
        assertEquals(emptyList(), store.installed.value)
        assertNull(StoreFiles.readBytes("$root/manifest.json"))
        assertTrue(filesUnder("$root/code").isEmpty())

        // Above the limit: refused by the client (its cap) and by the store itself.
        val large = code("large") + "\n// " + "x".repeat(2_000)
        http.route(CODE_URL, large)
        assertStoreFailure(Reason.TOO_LARGE) { store.download(offer) }
        http.enforceBodyCap = false
        assertStoreFailure(Reason.TOO_LARGE) { store.download(offer) }
        assertStoreFailure(Reason.TOO_LARGE) { store.install(offer, sha256Hex(large)) }
        assertEquals(emptyList(), store.installed.value)

        // An index above its limit is not added.
        val small = newStore(limits = IosJsSourceStore.Limits(indexBytes = 100)).loaded()
        assertStoreFailure(Reason.TOO_LARGE) { small.addRepository("https://repo.test/other/index.json".also { http.route(it, index("1.0.0")) }) }
    }

    @Test
    fun onlyHttpsIsFetchedAlsoAfterRedirects() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        assertStoreFailure(Reason.INSECURE_URL) { store.addRepository("http://repo.test/repo/index.json") }
        assertStoreFailure(Reason.INSECURE_URL) { store.addRepository("file:///etc/index.json") }
        assertStoreFailure(Reason.INSECURE_URL) { store.addRepository("not a url") }
        assertStoreFailure(Reason.INVALID_INDEX) {
            store.addRepository("https://repo.test/broken.json".also { http.route(it, """{"not":"an index"}""") })
        }
        assertStoreFailure(Reason.DOWNLOAD_FAILED) { store.addRepository("https://repo.test/missing.json") }
        assertEquals(emptyList(), store.repositories.first())
        assertEquals(null, storedRepositories())

        // The code URL redirects to another https host (relative locations resolve against it): the confirmation
        // names both hosts.
        store.addRepository(REPO_URL)
        http.redirect(CODE_URL, "https://cdn.test/first/source.js")
        http.redirect("https://cdn.test/first/source.js", "../code/source.js", status = 301)
        http.route("https://cdn.test/code/source.js", code("v1"))
        val download = store.download(store.offer(ID)!!)
        assertEquals("cdn.test", download.host)
        assertEquals("repo.test", download.requestedHost)
        assertTrue(download.redirected)
        assertEquals(sha256Hex(code("v1")), download.sha256)

        // A redirect to plain HTTP is not followed.
        http.redirect(CODE_URL, "http://cdn.test/code/source.js")
        http.clearRequests()
        assertStoreFailure(Reason.INSECURE_URL) { store.download(store.offer(ID)!!) }
        assertEquals(listOf(CODE_URL), http.urls)

        // Nor are endless redirects.
        http.redirect(CODE_URL, CODE_URL)
        assertStoreFailure(Reason.DOWNLOAD_FAILED) { store.download(store.offer(ID)!!) }
    }

    @Test
    fun uninstallRemovesFilesAndPreferences() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        val source = store.installFixture()
        val sha = manifest().single().sha256
        val preferences = store.runtime.preferences(source.descriptor)
        preferences.setValue("k", JsPreferenceValue.Bool(false))
        preferences.setString("token", "secret")
        assertNotNull(suite().objectForKey("k"))

        store.uninstall(ID)
        assertEquals(emptyList(), store.installed.value)
        assertEquals(emptyList(), store.sources.first { it.isEmpty() })
        assertNull(store.source(source.sourceId))
        assertEquals(emptyList(), manifest())
        assertNull(file("code/$sha.js"))
        assertNull(file("icons/$ID"))
        assertNull(store.iconFileForSource(source.sourceId))
        // The preferences are gone: a new suite object and the runtime's own read nothing.
        assertNull(suite().objectForKey("k"))
        assertNull(store.runtime.preferences(source.descriptor).getValue("k"))
        assertNull(store.runtime.preferences(source.descriptor).getString("token"))
        // Still offered by the repository, now as not installed.
        val item = store.item()
        assertNull(item.installed)
        // Uninstalling what is not installed does nothing.
        store.uninstall(ID)
    }

    @Test
    fun aReloadedStoreUsesTheCachedMetadataWithoutRunningTheScript() = timedTest {
        serveRepository()
        val first = newStore().loaded()
        first.installFixture()
        assertTrue(scriptLog.value.any { it.endsWith("preferences read") })
        first.close()

        scriptLog.value = emptyList()
        http.clearRequests()
        val again = newStore().loaded()
        val source = again.sources.first { it.isNotEmpty() }.single()
        assertEquals(listOf("k", "ua"), again.declaredPreferences(source.descriptor).map { it.key })
        assertTrue(again.declaredPreferences(source.descriptor).first() is JsPreference.CheckBox)
        // Nothing was fetched and no script ran to get there.
        assertEquals(emptyList(), http.urls)
        assertEquals(emptyList(), scriptLog.value)
        // The repositories were kept in the defaults (not read again until a refresh).
        assertEquals(listOf(REPO_URL), again.repositories.first().map { it.url })
        assertNull(again.repositories.first().single().entries)
        // Not obsolete while the repository was not read.
        assertEquals(JsSourceStatus.UP_TO_DATE, again.item().status)
        assertEquals("First v1", source.extension.getPopular(1).list.single().name)
    }

    @Test
    fun aCorruptManifestStartsEmptyAndIsSetAside() = timedTest {
        StoreFiles.writeAtomically("$root/manifest.json", "{ not json".encodeToByteArray())
        serveRepository()
        val store = newStore(clock = { 42L }).loaded()
        assertEquals(emptyList(), store.installed.value)
        assertEquals("{ not json", file("manifest.json.corrupt-42")?.decodeToString())
        assertNull(file("manifest.json"))
        assertTrue(storeLog.value.any { it.contains("manifest could not be read") })
        // The store works from there.
        store.installFixture()
        assertEquals(1, manifest().size)
    }

    @Test
    fun aCorruptCodeFileIsALoadErrorAndTheOthersStillLoad() = timedTest {
        serveRepository()
        val first = newStore().loaded()
        first.installFixture()
        // A second source from another repository.
        val otherCode = code("other", id = OTHER_ID)
        http.route("https://other.test/index.json", index("2.0.0", id = OTHER_ID, codeUrl = "https://other.test/other.js", name = "Other"))
        http.route("https://other.test/other.js", otherCode)
        first.addRepository("https://other.test/index.json")
        first.install(first.offer(OTHER_ID)!!, sha256Hex(otherCode))
        first.close()
        val sha = manifest().single { it.id == ID }.sha256

        // The first source's text was changed on disk.
        StoreFiles.writeAtomically("$root/code/$sha.js", "tampered".encodeToByteArray())
        val again = newStore().loaded()
        assertEquals(listOf(OTHER_ID), again.sources.first { it.isNotEmpty() }.map { it.id })
        assertTrue(again.item(ID).loadError!!.contains("does not match its SHA-256"))
        assertNull(again.item(OTHER_ID).loadError)
        again.close()

        // Missing, and a manifest record whose SHA-256 is no hash (it must not name a path).
        NSFileManager.defaultManager.removeItemAtPath("$root/code/$sha.js", null)
        val records = manifest().map { if (it.id == OTHER_ID) it.copy(sha256 = "../../manifest") else it }
        StoreFiles.writeAtomically(
            "$root/manifest.json",
            json.encodeToString(JsSourceManifest.serializer(), JsSourceManifest(sources = records)).encodeToByteArray(),
        )
        val third = newStore().loaded()
        assertTrue(third.item(ID).loadError!!.contains("missing"))
        assertTrue(third.item(OTHER_ID).loadError!!.contains("malformed"))
        assertEquals(emptyList(), third.sources.value)
        // Reinstalling repairs it.
        third.addRepository(REPO_URL)
        third.install(third.offer(ID)!!, sha)
        assertNull(third.item(ID).loadError)
    }

    @Test
    fun nsfwSourcesAreLeftOutWhileHidden() = timedTest {
        serveRepository()
        http.route(REPO_URL, index("1.0.0", nsfw = true))
        val store = newStore().loaded()
        store.installFixture()
        assertEquals(1, store.sources.first { it.isNotEmpty() }.size)
        showNsfw.value = false
        assertEquals(emptyList(), store.sources.first { it.isEmpty() })
        // Still installed and listed.
        assertNotNull(store.item().installed)
        // Not offered while hidden when not installed.
        store.uninstall(ID)
        assertTrue(store.items.first().none { it.id == ID })
    }

    @Test
    fun aRemovedRepositoryMakesItsSourcesObsolete() = timedTest {
        serveRepository()
        val store = newStore().loaded()
        store.installFixture()
        // A second repository that does not offer the source.
        http.route("https://other.test/index.json", "[]")
        store.addRepository("https://other.test/index.json")
        assertEquals(JsSourceStatus.UP_TO_DATE, store.item().status)
        store.removeRepository(REPO_URL)
        assertEquals(listOf("https://other.test/index.json"), storedRepositories())
        assertEquals(JsSourceStatus.OBSOLETE, store.item().status)
        // With no repository at all nothing says it is gone.
        store.removeRepository("https://other.test/index.json")
        assertEquals(JsSourceStatus.UP_TO_DATE, store.item().status)
        assertStoreFailure(Reason.NOT_FOUND) { store.downloadUpdate(ID) }
    }

    private fun storedRepositories(): List<String>? =
        repositoryDefaults.arrayForKey(IosJsSourceStore.REPOSITORIES_KEY)?.map { it as String }

    private fun filesUnder(path: String): List<String> =
        NSFileManager.defaultManager.subpathsOfDirectoryAtPath(path, null)?.filterIsInstance<String>().orEmpty()

    private companion object {
        const val ID = 652112892L
        const val OTHER_ID = 777L
        const val REPO_URL = "https://repo.test/repo/index.json"
        const val REPO_INFO_URL = "https://repo.test/repo/repo.json"
        const val CODE_URL = "https://repo.test/code/source.js"
        const val ICON_URL = "https://repo.test/icon.png"
        const val SITE = "https://site.test"

        val json = Json { ignoreUnknownKeys = true }
    }
}
