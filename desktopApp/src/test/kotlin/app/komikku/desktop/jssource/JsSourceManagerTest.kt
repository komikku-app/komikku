package app.komikku.desktop.jssource

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.source.DesktopSourceManager
import app.komikku.shared.jssource.MangayomiSourceStore
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.mihon.MangayomiJsSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The desktop source manager with JavaScript sources: they are merged with the local source, recorded as stubs under
 * their "[JS]" name (so an uninstalled source keeps its download folder) and removed again; the app's source manager is
 * wired to the store from DI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsSourceManagerTest {

    private lateinit var app: Application
    private lateinit var runtime: MangayomiJsRuntime

    @BeforeAll
    fun setUp(@TempDir dir: File) {
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
