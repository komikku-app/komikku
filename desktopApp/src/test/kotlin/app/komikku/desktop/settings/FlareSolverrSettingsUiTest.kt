package app.komikku.desktop.settings

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.webview.DesktopCloudflarePreferences
import app.komikku.desktop.webview.FlareSolverrBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.NoBrowserBackend
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P8 settings, headless: Settings → Browse has the FlareSolverr group (address, "Test connection", the privacy note);
 * the app's backend is the FlareSolverr one, and "Test connection" reports a missing address and a ready service; the
 * address' password is never shown (P8 review).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlareSolverrSettingsUiTest {

    private lateinit var app: Application
    private val solver = MockWebServer()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        solver.start()
        app = DesktopInjekt.init(dataDir)
    }

    @AfterAll
    fun stop() {
        solver.close()
        BrowserBackends.current = NoBrowserBackend
    }

    @Test
    fun `test connection from the settings`() = runDesktopComposeUiTest(width = 1200, height = 1400) {
        Injekt.get<ReaderLauncher>().close()
        // Creating the network installs the backend on the setting.
        Injekt.get<NetworkHelper>()
        (BrowserBackends.current is FlareSolverrBackend) shouldBe true

        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-settings")
        onNodeWithTag("more-settings").performClick()
        waitForTag("settings-browse")
        onNodeWithTag("settings-browse").performClick()
        waitForTag("settings-Browse")
        val test = stringResource(KMR.strings.desktop_flaresolverr_test)
        onNodeWithTag("settings-Browse").performScrollToNode(hasText(test))
        waitForText(stringResource(KMR.strings.desktop_flaresolverr_url))

        // Off by default: the test says so.
        onNodeWithText(test).performClick()
        waitForText(stringResource(KMR.strings.desktop_flaresolverr_not_configured))
        solver.requestCount shouldBe 0

        // A ready service.
        solver.enqueue(MockResponse.Builder().code(200).body("""{"msg": "FlareSolverr is ready!", "version": "3.3.21"}""").build())
        Injekt.get<DesktopCloudflarePreferences>().flareSolverrUrl().set(solver.url("/").toString())
        waitForText(solver.url("/").toString())
        onNodeWithText(test).performClick()
        waitForText(stringResource(KMR.strings.desktop_flaresolverr_ok, "3.3.21"))
        solver.requestCount shouldBe 1
        solver.takeRequest()

        // P8 review: an address with credentials never shows its password (also with a `%` in the address, which the
        // subtitle's formatting used to trip over), and the test still sends it.
        solver.enqueue(MockResponse.Builder().code(200).body("""{"msg": "FlareSolverr is ready!", "version": "3.3.22"}""").build())
        val address = "http://user:secret@${solver.hostName}:${solver.port}/a%2Fb/"
        val masked = "http://user:••••@${solver.hostName}:${solver.port}/a%2Fb/"
        Injekt.get<DesktopCloudflarePreferences>().flareSolverrUrl().set(address)
        waitForText(masked)
        onAllNodesWithText("secret", substring = true).fetchSemanticsNodes().isEmpty() shouldBe true
        onNodeWithText(test).performClick()
        waitForText(stringResource(KMR.strings.desktop_flaresolverr_ok, "3.3.22"))
        val sent = solver.takeRequest()
        sent.url.encodedPath shouldBe "/a%2Fb/"
        sent.headers["Authorization"] shouldBe okhttp3.Credentials.basic("user", "secret")

        // The edit field shows the masked address too.
        onNodeWithText(stringResource(KMR.strings.desktop_flaresolverr_url)).performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(masked, substring = true).fetchSemanticsNodes().size >= 2 }
        onAllNodesWithText("secret", substring = true).fetchSemanticsNodes().isEmpty() shouldBe true
    }

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
