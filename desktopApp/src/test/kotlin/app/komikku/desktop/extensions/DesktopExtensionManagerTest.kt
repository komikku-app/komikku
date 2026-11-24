package app.komikku.desktop.extensions

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import app.komikku.shared.extension.GetPlatformExtensionsByType
import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsEvent
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreenModel
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * End to end against [MockWebServer]: a generated extension store index and generated, signed
 * extension APKs (dex code from AOSP dx) → add store → find available → install → sources registered
 * in the desktop `SourceManager` → update → untrusted / trust → reload from disk → uninstall.
 * No real network, no third-party APKs.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DesktopExtensionManagerTest {

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, ByteArray>()

    private lateinit var dataDir: File
    private lateinit var work: File
    private lateinit var storeKey: TestSigningKey
    private lateinit var manager: DesktopExtensionManager

    private val pkg = "eu.kanade.tachiyomi.extension.en.dexfixture"
    private val sourceClass = "eu/kanade/tachiyomi/extension/en/dexfixture/DexFixtureSource"
    private val sourceId = 4242L

    @BeforeAll
    fun setUp(@TempDir dir: File) {
        dataDir = File(dir, "data")
        work = File(dir, "work").apply { mkdirs() }
        storeKey = TestSigningKey.rsa("Fixture store")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val bytes = routes[request.url.encodedPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()
            }
        }
        server.start()

        DesktopInjekt.init(dataDir)
        manager = Injekt.get()
        routes["/apk/fixture-3.apk"] = fixtureApk(versionCode = 3, name = "Dex fixture", key = storeKey)
        routes["/index.min.json"] = storeIndex(versionCode = 3, apk = "fixture-3.apk")
    }

    @AfterAll
    fun tearDown() {
        server.close()
    }

    private fun fixtureApk(versionCode: Int, name: String, key: TestSigningKey, packageName: String = pkg): ByteArray {
        val jar = File(work, "classes-${System.nanoTime()}.jar")
        val className = packageName.replace('.', '/') + "/DexFixtureSource"
        FixtureClasses.jar(jar, mapOf(className to FixtureClasses.source(className, sourceId, name)))
        val apk = File(work, "${packageName.substringAfterLast('.')}-$versionCode-${System.nanoTime()}.apk")
        FixtureApk(
            packageName = packageName,
            versionName = "1.4.$versionCode",
            versionCode = versionCode,
            sourceClass = ".DexFixtureSource",
            dex = FixtureClasses.dex(jar, work),
        ).writeSigned(apk, key)
        return apk.readBytes()
    }

    /** A Komikku / Mihon extension store index (JSON) with one extension. */
    private fun storeIndex(versionCode: Int, apk: String): ByteArray = """
        {
          "name": "Fixture store",
          "badgeLabel": "FIX",
          "signingKey": "${storeKey.sha256}",
          "contact": { "website": "${server.url("/")}" },
          "extensionList": {
            "extensions": [
              {
                "name": "Dex fixture",
                "packageName": "$pkg",
                "resources": { "apkUrl": "${server.url("/apk/$apk")}", "iconUrl": "${server.url("/icon.png")}" },
                "extensionLib": "1.4",
                "versionCode": $versionCode,
                "versionName": "1.4.$versionCode",
                "contentWarning": "SAFE",
                "sources": [ { "id": $sourceId, "name": "Dex fixture", "language": "en", "homeUrl": "https://example.invalid" } ]
              }
            ]
          }
        }
    """.trimIndent().toByteArray()

    private fun indexUrl() = server.url("/index.min.json").toString()

    @Test
    @Order(1)
    fun `adds a store and lists its extensions`(): Unit = runBlocking {
        Injekt.get<PlatformExtensionManager>() shouldBeSameInstanceAs manager
        withTimeout(30_000) { manager.isInitialized.first { it } }
        manager.installedExtensionsFlow.value.shouldBeEmpty()

        Injekt.get<AddExtensionStore>()(indexUrl()).isSuccess shouldBe true
        Injekt.get<GetExtensionStores>().get().single().signingKey shouldBe storeKey.sha256

        manager.findAvailableExtensions()

        val available = manager.availableExtensionsFlow.value.single()
        available.pkgName shouldBe pkg
        available.versionCode shouldBe 3L
        available.signatureHash shouldBe storeKey.sha256
        available.sources.single().id shouldBe sourceId
        manager.getSourceData(sourceId)?.name shouldBe "Dex fixture"
        Injekt.get<GetPlatformExtensionsByType>().subscribe().first().available.single().name shouldBe "Dex fixture"
    }

    @Test
    @Order(2)
    fun `installs an extension and registers its sources`(): Unit = runBlocking {
        val steps = manager.installExtension(manager.availableExtensionsFlow.value.single()).toList()

        steps shouldContainExactly listOf(InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing, InstallStep.Installed)
        val installed = manager.installedExtensionsFlow.value.single()
        installed.pkgName shouldBe pkg
        installed.versionCode shouldBe 3L
        installed.signatureHash shouldBe storeKey.sha256
        installed.storeName shouldBe "FIX"
        installed.sources.single().id shouldBe sourceId
        File(dataDir, "extensions/apks/$pkg.apk").isFile shouldBe true

        val sourceManager = Injekt.get<SourceManager>()
        withTimeout(10_000) { sourceManager.sources.first { sources -> sources.any { it.id == sourceId } } }
        sourceManager.get(sourceId)?.name shouldBe "Dex fixture"
        manager.getExtensionPackage(sourceId) shouldBe pkg
    }

    @Test
    @Order(3)
    fun `updates to a newer version from the store`(): Unit = runBlocking {
        routes["/apk/fixture-4.apk"] = fixtureApk(versionCode = 4, name = "Dex fixture v4", key = storeKey)
        routes["/index.min.json"] = storeIndex(versionCode = 4, apk = "fixture-4.apk")

        manager.findAvailableExtensions()
        val outdated = manager.installedExtensionsFlow.value.single()
        outdated.hasUpdate shouldBe true
        Injekt.get<SourcePreferences>().extensionUpdatesCount().get() shouldBe 1

        // P22 review: an update replaces the entry in one emission; an open extension info stays open across it
        val seen = mutableListOf<List<String>>()
        val recorder = CoroutineScope(Dispatchers.Unconfined).launch {
            manager.installedExtensionsFlow.collect { list -> synchronized(seen) { seen += list.map { it.pkgName } } }
        }
        val details = ExtensionDetailsScreenModel(pkg, Injekt.get<Application>())
        val events = mutableListOf<ExtensionDetailsEvent>()
        val eventCollector = CoroutineScope(Dispatchers.Unconfined).launch { details.events.collect { synchronized(events) { events += it } } }
        withTimeout(10_000) { details.state.first { it.extension?.versionCode == 3L } }

        manager.updateExtension(outdated).toList().last() shouldBe InstallStep.Installed
        withTimeout(10_000) { details.state.first { it.extension?.versionCode == 4L } }
        recorder.cancel()
        eventCollector.cancel()
        details.onDispose()
        synchronized(seen) { seen.filter { pkg !in it } }.shouldBeEmpty()
        synchronized(events) { events.toList() }.shouldBeEmpty()

        val updated = manager.installedExtensionsFlow.value.single()
        updated.versionCode shouldBe 4L
        updated.hasUpdate shouldBe false
        Injekt.get<SourcePreferences>().extensionUpdatesCount().get() shouldBe 0
        withTimeout(10_000) {
            Injekt.get<SourceManager>().sources.first { sources -> sources.any { it.name == "Dex fixture v4" } }
        }
    }

    @Test
    @Order(4)
    fun `refuses downgrades and other signers`(): Unit = runBlocking {
        val older = File(work, "older.apk").apply { writeBytes(fixtureApk(versionCode = 2, name = "Old", key = storeKey)) }
        manager.installApkFile(older) shouldBe false

        val otherSigner = File(work, "other-signer.apk")
            .apply { writeBytes(fixtureApk(versionCode = 9, name = "Evil", key = TestSigningKey.rsa("Someone else"))) }
        manager.installApkFile(otherSigner) shouldBe false

        manager.installedExtensionsFlow.value.single().versionCode shouldBe 4L
    }

    @Test
    @Order(5)
    fun `extensions signed by unknown keys are untrusted until trusted`(): Unit = runBlocking {
        val otherPkg = "eu.kanade.tachiyomi.extension.en.untrusted"
        val apk = File(work, "untrusted.apk")
            .apply { writeBytes(fixtureApk(versionCode = 1, name = "Untrusted", key = TestSigningKey.rsa("Unknown"), packageName = otherPkg)) }

        manager.installApkFile(apk) shouldBe true
        val untrusted = manager.untrustedExtensionsFlow.value.single()
        untrusted.pkgName shouldBe otherPkg
        manager.installedExtensionsFlow.value.map { it.pkgName } shouldContainExactly listOf(pkg)

        manager.trust(untrusted)

        manager.untrustedExtensionsFlow.value.shouldBeEmpty()
        manager.installedExtensionsFlow.value.map { it.pkgName }.toSet() shouldBe setOf(pkg, otherPkg)
        Injekt.get<SourcePreferences>().trustedExtensions().get() shouldContain "$otherPkg:1:${untrusted.signatureHash}"
        manager.uninstall(otherPkg)
    }

    @Test
    @Order(6)
    fun `a new manager loads the installed extensions from disk`(): Unit = runBlocking {
        val reloaded = DesktopExtensionManager(
            context = Injekt.get<Application>(),
            extensionsDir = File(dataDir, "extensions"),
            runtime = Injekt.get<ExtensionRuntime>(),
            network = Injekt.get(),
            storeRepository = Injekt.get<ExtensionStoreRepository>(),
            preferences = Injekt.get(),
            exhPreferences = Injekt.get(),
        )
        withTimeout(30_000) { reloaded.isInitialized.first { it } }

        val extension = reloaded.installedExtensionsFlow.value.single()
        extension.pkgName shouldBe pkg
        extension.versionCode shouldBe 4L
        extension.sources.single().id shouldBe sourceId
        File(dataDir, "extensions/jars").listFiles().orEmpty().filter { it.extension == "jar" }.size shouldBe 1
    }

    @Test
    @Order(7)
    fun `uninstalls an extension`(): Unit = runBlocking {
        manager.uninstall(pkg)

        manager.installedExtensionsFlow.value.shouldBeEmpty()
        File(dataDir, "extensions/apks/$pkg.apk").exists() shouldBe false
        val sourceManager = Injekt.get<SourceManager>()
        withTimeout(10_000) { sourceManager.sources.first { sources -> sources.none { it.id == sourceId } } }
        sourceManager.get(sourceId) shouldBe null
        // Library entries of the removed source still resolve to a named stub
        sourceManager.getOrStub(sourceId).name.shouldNotBeNull()
    }
}
