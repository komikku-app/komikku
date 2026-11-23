package app.komikku.desktop.jssource

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.source.DesktopSourceManager
import app.komikku.shared.jssource.InstalledJsSource
import app.komikku.shared.jssource.JsSourcePreferences
import app.komikku.shared.jssource.MangayomiSourceStore
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.mihon.MangayomiJsSource
import app.komikku.sourcejs.sha256Hex
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The desktop source manager with JavaScript sources: they are merged with the local source, recorded as stubs under
 * their "[JS]" name (so an uninstalled source keeps its download folder) and removed again; the app's source manager is
 * wired to the store from DI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsSourceManagerTest {

    private lateinit var app: Application
    private lateinit var runtime: MangayomiJsRuntime
    private lateinit var tempDir: File

    @BeforeAll
    fun setUp(@TempDir dir: File) {
        tempDir = dir
        app = DesktopInjekt.init(File(dir, "data"))
        runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>())
    }

    @AfterAll
    fun tearDown() {
        runtime.close()
    }

    private val code = """
        class DefaultExtension extends MProvider {
          get supportsLatest() { return false; }
          async getPopular(page) { return { list: [], hasNextPage: false }; }
          getFilterList() { return []; }
          getSourcePreferences() { return []; }
        }
    """.trimIndent()

    @Test
    fun `merges JavaScript sources and keeps their stubs`(): Unit = runBlocking {
        val source = runtime.source(JsSourceDescriptor(id = 31, name = "Merge JS", lang = "pt-br", baseUrl = "https://example.org"), code)
        val jsSources = MutableStateFlow<List<Source>>(emptyList())
        val stubs = Injekt.get<StubSourceRepository>()
        val manager = DesktopSourceManager(
            context = app,
            fileSystem = Injekt.get(),
            coverManager = Injekt.get(),
            stubSourceRepository = stubs,
            allowHiddenFiles = { false },
            jsSources = jsSources,
        )
        withTimeout(10_000) { manager.isInitialized.first { it } }

        jsSources.value = listOf(source)
        withTimeout(10_000) { manager.sources.first { list -> list.any { it.id == source.id } } }
        manager.get(source.id) shouldBeSameInstanceAs source
        manager.getOnlineSources() shouldContain source
        manager.getAll().size shouldBe 2

        val stub = withTimeout(10_000) {
            stubs.subscribeAll().first { list -> list.any { it.id == source.id } }.single { it.id == source.id }
        }
        stub.lang shouldBe "pt-BR"
        stub.name shouldBe "Merge JS [JS]"
        // Same download folder name as the live source (DownloadProvider uses toString()).
        stub.toString() shouldBe source.toString()

        jsSources.value = emptyList()
        withTimeout(10_000) { manager.sources.first { list -> list.none { it.id == source.id } } }
        manager.getOnlineSources() shouldNotContain source
        val fallback = manager.getOrStub(source.id)
        fallback.shouldBeInstanceOf<StubSource>()
        fallback.toString() shouldBe source.toString()
    }

    /** A store directory with one installed source (no cached metadata) and a store whose loading waits for [gate]. */
    private fun slowStore(name: String, mangayomiId: Long, gate: CountDownLatch): Pair<MangayomiSourceStore, ExecutorService> {
        val dir = File(tempDir, name)
        val descriptor = JsSourceDescriptor(id = mangayomiId, name = "Slow JS", lang = "en", baseUrl = "https://example.org")
        val sha256 = sha256Hex(code)
        File(dir, "code/$sha256.js").apply { parentFile.mkdirs() }.writeText(code)
        val record = InstalledJsSource(descriptor, repoUrl = "https://example.org/index.json", sha256 = sha256, installedAt = 0, updatedAt = 0)
        File(dir, MangayomiSourceStore.MANIFEST).writeText(
            """{"version":1,"sources":[${Json.encodeToString(InstalledJsSource.serializer(), record)}]}""",
        )
        // The store's scope runs on one thread that is busy until the gate opens, so its files are not read before.
        val executor = Executors.newSingleThreadExecutor()
        executor.execute { gate.await(60, TimeUnit.SECONDS) }
        val store = MangayomiSourceStore(
            directory = dir,
            preferences = JsSourcePreferences(Injekt.get<PreferenceStore>()),
            runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>()),
            client = { Injekt.get<NetworkHelper>().client },
            scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()),
        )
        return store to executor
    }

    private fun manager(jsSources: Flow<List<Source>>) = DesktopSourceManager(
        context = app,
        fileSystem = Injekt.get(),
        coverManager = Injekt.get(),
        stubSourceRepository = Injekt.get(),
        allowHiddenFiles = { false },
        jsSources = jsSources,
    )

    @Test
    fun `the manager waits for a store that is still loading`(): Unit = runBlocking {
        val gate = CountDownLatch(1)
        val (store, executor) = slowStore("slow", 32, gate)
        try {
            val manager = manager(MangayomiSourceStore.sourcesOrEmpty { store })
            delay(1_000)
            // Not initialized while the store loads: a queued download of the source is not dropped as unknown.
            manager.isInitialized.value shouldBe false
            gate.countDown()
            withTimeout(20_000) { manager.isInitialized.first { it } }
            manager.get(JsSourceIds.komikkuId(32)).shouldBeInstanceOf<MangayomiJsSource>()
        } finally {
            gate.countDown()
            store.close()
            executor.shutdown()
        }
    }

    @Test
    fun `the manager does not wait for a store forever`(): Unit = runBlocking {
        val gate = CountDownLatch(1)
        val (store, executor) = slowStore("stuck", 33, gate)
        try {
            val manager = manager(MangayomiSourceStore.sourcesOrEmpty(loadTimeoutMillis = 300) { store })
            withTimeout(10_000) { manager.isInitialized.first { it } }
            manager.get(JsSourceIds.komikkuId(33)) shouldBe null
            // The sources still follow once the store loaded them.
            gate.countDown()
            withTimeout(20_000) { manager.sources.first { list -> list.any { it.id == JsSourceIds.komikkuId(33) } } }
        } finally {
            gate.countDown()
            store.close()
            executor.shutdown()
        }
    }

    @Test
    fun `a store that cannot be built counts as loaded without sources`(): Unit = runBlocking {
        val manager = manager(MangayomiSourceStore.sourcesOrEmpty { error("no store") })
        withTimeout(10_000) { manager.isInitialized.first { it } }
        manager.getOnlineSources().none { it is MangayomiJsSource } shouldBe true
    }

    @Test
    fun `the app's source manager follows the store`(): Unit = runBlocking {
        val store = Injekt.get<MangayomiSourceStore>()
        store.directory shouldBe File(app.filesDir, MangayomiSourceStore.DIRECTORY)
        withTimeout(10_000) { store.isLoaded.first { it } }
        store.installed.value.shouldNotBeNull()
        val manager = Injekt.get<SourceManager>()
        manager.shouldBeInstanceOf<DesktopSourceManager>()
        withTimeout(30_000) { manager.isInitialized.first { it } }
        manager.getOnlineSources().none { it is MangayomiJsSource } shouldBe true
    }
}
