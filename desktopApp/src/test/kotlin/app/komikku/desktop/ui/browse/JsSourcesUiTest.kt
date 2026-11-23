package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.jssource.MangayomiSourceStore
import app.komikku.shared.jssource.TAG_ADD_REPOSITORY_CONFIRM
import app.komikku.shared.jssource.TAG_INSTALL_CONFIRM
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.sha256Hex
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.shouldBe
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
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the desktop JavaScript sources tab against [MockWebServer]: add a Mangayomi repository through
 * the dialog, install its source after the confirmation (host and SHA-256 shown), find it with its "JS" label in the
 * Sources tab and browse it, with its notes above the catalogue.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsSourcesUiTest {

    private val server = MockWebServer()
    private lateinit var app: Application
    private lateinit var store: MangayomiSourceStore

    private val code = """
        class DefaultExtension extends MProvider {
          get supportsLatest() { return false; }
          async getPopular(page) {
            const res = await new Client().get(this.source.baseUrl + "/list");
            const items = new Document(res.body).select("a").map((a) => ({ name: a.text, link: a.attr("href") }));
            return { list: items, hasNextPage: false };
          }
          async getDetail(url) { return { name: "x", chapters: [] }; }
          getFilterList() { return []; }
          getSourcePreferences() { return []; }
        }
    """.trimIndent()

    @BeforeAll
    fun setUp(@TempDir dir: File) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.url.encodedPath) {
                    "/repo/index.json" -> """
                        [{"name":"UI JS source","id":4711,"lang":"en","baseUrl":"${server.url("/site")}",
                          "sourceCodeUrl":"${server.url("/code.js")}","version":"0.1.0","itemType":0,
                          "sourceCodeLanguage":1,"appMinVerReq":"0.5.0","isNsfw":false,"notes":"UI fixture notes"}]
                    """.trimIndent()
                    "/repo/repo.json" -> """{"name":"UI JS repo"}"""
                    "/code.js" -> code
                    "/site/list" -> """<html><body><a href="/m/1">UI JS manga</a></body></html>"""
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().code(200).body(Buffer().writeUtf8(body)).build()
            }
        }
        server.start()

        app = DesktopInjekt.init(File(dir, "data"))
        // The app's store, except that it may fetch from the (plain HTTP) fixture server.
        store = MangayomiSourceStore(
            directory = File(app.filesDir, MangayomiSourceStore.DIRECTORY),
            preferences = Injekt.get(),
            runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>()),
            client = { Injekt.get<NetworkHelper>().client },
            allowUrl = { it.isHttps || it.host == server.hostName },
        )
        Injekt.addSingleton(store)
    }

    @AfterAll
    fun tearDown() {
        store.close()
        server.close()
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `add a repository, install a source after confirming it, browse it`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.text()).performClick()
        onNodeWithText(KMR.strings.js_sources.text()).performClick()
        waitForText(KMR.strings.js_sources_empty.text())

        // Add the repository through the dialog
        onNodeWithText(KMR.strings.js_source_repo_add.text()).performClick()
        onNode(hasSetTextAction()).performTextInput(server.url("/repo/index.json").toString())
        onNodeWithTag(TAG_ADD_REPOSITORY_CONFIRM).performClick()
        waitForText("UI JS repo")
        waitForText("UI JS source")
        waitForText("UI fixture notes")

        // Install: the confirmation shows the host and the SHA-256 of the downloaded text
        onNodeWithText(MR.strings.ext_install.text()).performClick()
        waitForText(sha256Hex(code))
        waitForText(server.hostName)
        onNodeWithTag(TAG_INSTALL_CONFIRM).performClick()
        waitForText(MR.strings.ext_uninstall.text())
        waitForIdle()
        store.installed.value!!.single().sha256 shouldBe sha256Hex(code)

        // The source is in the Sources tab with its "JS" label; its catalogue shows the notes and the list
        onNodeWithText(MR.strings.label_sources.text()).performClick()
        waitForText("UI JS source")
        waitForText(KMR.strings.js_source_label.text())
        onNodeWithText("UI JS source").performClick()
        waitForText("UI fixture notes")
        waitForText("UI JS manga")
    }
}
