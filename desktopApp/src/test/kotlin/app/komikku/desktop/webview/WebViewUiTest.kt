package app.komikku.desktop.webview

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P7 end to end in a headless window: a source behind a Cloudflare check shows the interceptor's "no built-in browser"
 * error with Android's retry / WebView actions; "Open in WebView" (the system browser cannot be opened here) shows the
 * page address and the cookie dialog; the pasted `cf_clearance` gets the retry past the check.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebViewUiTest {

    private lateinit var app: Application
    private val server = MockWebServer()
    private val opened = mutableListOf<String>()

    private inner class ProtectedSource : HttpSource() {
        override val name = "Protected source"
        override val lang = "en"
        override val baseUrl: String get() = server.url("").toString().trimEnd('/')
        override val supportsLatest = false

        override suspend fun getPopularManga(page: Int): MangasPage {
            client.newCall(GET("$baseUrl/popular", headers)).awaitSuccess().close()
            return MangasPage(
                listOf(
                    SManga.create().apply {
                        url = "/m/1"
                        title = "Protected manga"
                    },
                ),
                false,
            )
        }
    }

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if ("cf_clearance=abc" in request.headers["Cookie"].orEmpty()) {
                    MockResponse.Builder().code(200).body("ok").build()
                } else {
                    MockResponse.Builder().code(403).addHeader("Server", "cloudflare")
                        .body(DesktopCloudflareInterceptorTest.CHALLENGE_BODY).build()
                }
        }
        server.start()
        app = DesktopInjekt.init(dataDir)
        // A source opens its catalogue directly here (the source feed is SourceFeedUiTest's)
        Injekt.get<UiPreferences>().useNewSourceNavigation().set(false)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(ProtectedSource())) }
        // The system browser cannot be opened in a headless test: the dialog shows the address instead.
        Injekt.addSingletonFactory {
            DesktopWebView(Injekt.get(), Injekt.get(), Injekt.get(), browserOpener = {
                opened += it
                false
            })
        }
    }

    @AfterAll
    fun stop() {
        server.close()
    }

    @Test
    fun `pass a Cloudflare check by hand from the catalogue error`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForText("Protected source")
        onNodeWithText("Protected source").performClick()

        // The interceptor's message, with the retry and WebView actions
        waitForText("is protected by Cloudflare")
        onNodeWithText(MR.strings.action_open_in_web_view.localized()).performClick()

        waitForTag("webview-manual")
        onNodeWithText(stringResource(KMR.strings.desktop_webview_manual_title, server.hostName)).assertExists()
        onNodeWithTag("webview-url").assertExists()
        opened shouldBe listOf(server.url("/").toString())

        // An invalid paste keeps the dialog open with the reason
        onNodeWithTag("webview-cookies").performTextInput("not a cookie")
        onNodeWithTag("webview-save").performClick()
        waitForTag("webview-error")

        onNodeWithTag("webview-cookies").performTextClearance()
        onNodeWithTag("webview-cookies").performTextInput("cf_clearance=abc")
        onNodeWithTag("webview-save").performClick()
        waitForTag("webview-manual", present = false)
        waitForText(stringResource(KMR.strings.desktop_webview_cookies_saved, server.hostName))

        // The retry carries the pasted clearance
        onNodeWithText(MR.strings.action_retry.localized()).performClick()
        waitForText("Protected manga")
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }
}
