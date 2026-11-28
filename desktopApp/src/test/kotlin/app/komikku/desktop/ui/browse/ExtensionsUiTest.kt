package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import app.komikku.desktop.extensions.DesktopExtensionManager
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.runBlocking
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
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the Extensions tab against [MockWebServer]: add a (generated) extension store
 * through the stores dialog, see its extension as available, install it (generated, signed APK with
 * dex code), and find its source in the Sources tab; an extension that needs what desktop cannot do is listed as
 * unsupported, with what it needs.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ExtensionsUiTest {

    private val server = MockWebServer()
    private lateinit var app: Application
    private val routes = mutableMapOf<String, ByteArray>()
    private lateinit var unsupportedApk: File

    @BeforeAll
    fun setUp(@TempDir dir: File) {
        val key = TestSigningKey.rsa("UI store")
        val work = File(dir, "work").apply { mkdirs() }
        val className = "eu/kanade/tachiyomi/extension/en/uifixture/UiFixtureSource"
        val jar = File(work, "classes.jar")
        FixtureClasses.jar(jar, mapOf(className to FixtureClasses.source(className, 7777L, "UI fixture source")))
        val apk = File(work, "uifixture.apk")
        FixtureApk(
            packageName = "eu.kanade.tachiyomi.extension.en.uifixture",
            label = "Tachiyomi: UI fixture",
            sourceClass = ".UiFixtureSource",
            dex = FixtureClasses.dex(jar, work),
        ).writeSigned(apk, key)

        val loopingClass = "eu/kanade/tachiyomi/extension/en/uilooping/UiFixtureSource"
        val loopingJar = File(work, "looping.jar")
        val looping = FixtureClasses.source(loopingClass, 7778L, "UI looping source", constructorCall = "android/os/Looper" to "loop")
        FixtureClasses.jar(loopingJar, mapOf(loopingClass to looping))
        unsupportedApk = File(work, "uilooping.apk")
        FixtureApk(
            packageName = UNSUPPORTED_PKG,
            label = "Tachiyomi: UI looping",
            sourceClass = ".UiFixtureSource",
            dex = FixtureClasses.dex(loopingJar, work),
        ).writeSigned(unsupportedApk, key)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val bytes = synchronized(routes) { routes[request.url.encodedPath] } ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()
            }
        }
        server.start()
        synchronized(routes) {
            routes["/apk/uifixture.apk"] = apk.readBytes()
            routes["/index.min.json"] = """
                {
                  "name": "UI store",
                  "badgeLabel": "UI",
                  "signingKey": "${key.sha256}",
                  "contact": { "website": "${server.url("/")}" },
                  "extensionList": { "extensions": [ {
                    "name": "UI fixture",
                    "packageName": "eu.kanade.tachiyomi.extension.en.uifixture",
                    "resources": { "apkUrl": "${server.url("/apk/uifixture.apk")}", "iconUrl": "${server.url("/icon.png")}" },
                    "extensionLib": "1.4", "versionCode": 3, "versionName": "1.4.3", "contentWarning": "SAFE",
                    "sources": [ { "id": 7777, "name": "UI fixture source", "language": "en" } ]
                  } ] }
                }
            """.trimIndent().toByteArray()
        }

        app = DesktopInjekt.init(File(dir, "data"))
    }

    @AfterAll
    fun tearDown() {
        server.close()
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    @Order(1)
    fun `add a store, install an extension, use its source`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.text()).performClick()
        onNodeWithText(MR.strings.label_extensions.text()).performClick()
        waitForText(MR.strings.extensionStoresScreen_emptyLabel.text())

        // Add the store through the dialog
        onNodeWithText(MR.strings.extensionStores.text()).performClick()
        onNode(hasSetTextAction()).performTextInput(server.url("/index.min.json").toString())
        onNodeWithText(MR.strings.action_addExtensionStore.text()).performClick()
        waitForText("UI store")
        onNodeWithText(MR.strings.action_ok.text()).performClick()

        // Its extension is available; install it
        waitForText(KMR.strings.desktop_ext_available.text())
        waitForText("UI fixture source")
        onNodeWithText(MR.strings.ext_install.text()).performClick()
        waitForText(MR.strings.ext_installed.text())
        waitForText(MR.strings.ext_uninstall.text())

        // The extension's source is listed in the Sources tab
        onNodeWithText(MR.strings.label_sources.text()).performClick()
        waitForText("UI fixture source")
    }

    @Test
    @Order(2)
    fun `an extension that needs what desktop cannot do is listed as unsupported`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        // Signed by the store's key, which the first test trusted by adding the store
        runBlocking { Injekt.get<DesktopExtensionManager>().installApkFile(unsupportedApk, UNSUPPORTED_PKG) }
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.text()).performClick()
        onNodeWithText(MR.strings.label_extensions.text()).performClick()
        waitForText(KMR.strings.desktop_ext_unsupported.text())
        waitForText("UI looping")
        // "Unsupported on desktop: needs android.os.Looper.loop()", in the row
        onNodeWithText("android.os.Looper.loop()", substring = true).assertExists()
    }

    private companion object {
        const val UNSUPPORTED_PKG = "eu.kanade.tachiyomi.extension.en.uilooping"
    }
}
