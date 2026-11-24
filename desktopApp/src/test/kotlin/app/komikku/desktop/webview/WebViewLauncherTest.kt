package app.komikku.desktop.webview

import android.app.Application
import android.widget.Toast
import app.komikku.network.browser.BrowserSessionResult
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * The UI state of "Open in WebView" (P7 review): the address is resolved on click and off the UI thread, a failing
 * open does not leave the progress dialog up, a running session can be cancelled, and an embedded session's message
 * says what happened.
 */
class WebViewLauncherTest {

    @TempDir
    lateinit var dir: File

    private val app by lazy { Application(dir.resolve("data")) }
    private val webView = mockk<DesktopWebView>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toasts: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var previousPresenter: (CharSequence, Int) -> Unit

    private val launcher by lazy { WebViewLauncher(webView, scope, app) }

    @BeforeEach
    fun capture() {
        previousPresenter = Toast.presenter
        Toast.presenter = { text, _ -> toasts += text.toString() }
    }

    @AfterEach
    fun restore() {
        Toast.presenter = previousPresenter
        scope.cancel()
    }

    private fun WebViewLauncher.openAndWait(sourceId: Long? = 1L, resolve: () -> String?) = runBlocking {
        withTimeout(10.seconds) { open(sourceId, resolve)!!.join() }
    }

    @Test
    fun `the address is resolved off the calling thread, and a missing one is reported`() {
        val caller = Thread.currentThread()
        var resolvedOn: Thread? = null
        launcher.openAndWait {
            resolvedOn = Thread.currentThread()
            null
        }

        (resolvedOn != null && resolvedOn != caller) shouldBe true
        launcher.state shouldBe WebViewLauncher.State.Idle
        toasts shouldContainExactly listOf(app.stringResource(KMR.strings.desktop_webview_no_address))
        verify(exactly = 0) { webView.open(any(), any()) }
    }

    @Test
    fun `a failing open ends the progress state with an error`() {
        every { webView.open(any(), any()) } throws IllegalStateException("boom")

        launcher.openAndWait { "https://example.com/manga/1" }

        launcher.state shouldBe WebViewLauncher.State.Idle
        toasts shouldContainExactly listOf(app.stringResource(KMR.strings.desktop_webview_failed, "example.com", "boom"))

        // A failing address lookup too.
        launcher.openAndWait { throw IllegalArgumentException("no source") }
        launcher.state shouldBe WebViewLauncher.State.Idle
        toasts.size shouldBe 2
    }

    @Test
    fun `a running session can be cancelled`() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        every { webView.open(any(), any()) } answers {
            started.countDown()
            try {
                Thread.sleep(30_000)
            } catch (e: InterruptedException) {
                interrupted.countDown()
                throw e
            }
            DesktopWebView.Opened.InvalidUrl("never")
        }

        val job = launcher.open(1L) { "https://example.com/" }!!
        started.await(5, TimeUnit.SECONDS) shouldBe true
        launcher.state shouldBe WebViewLauncher.State.Running

        launcher.cancel()

        launcher.state shouldBe WebViewLauncher.State.Idle
        interrupted.await(5, TimeUnit.SECONDS) shouldBe true
        runBlocking { withTimeout(5.seconds) { job.join() } }
        job.isCancelled shouldBe true
        toasts shouldBe emptyList()
        // A new open works afterwards.
        every { webView.open(any(), any()) } returns DesktopWebView.Opened.InvalidUrl("x")
        launcher.openAndWait { "https://example.com/" }
        launcher.state shouldBe WebViewLauncher.State.Idle
    }

    @Test
    fun `an embedded session reports saved cookies, a timeout or nothing`() {
        val url = "https://example.com/".toHttpUrl()
        fun embedded(outcome: BrowserSessionResult.Outcome, cookies: List<Cookie>) =
            DesktopWebView.Opened.Embedded(BrowserSessionResult(outcome, url, cookies, null))

        every { webView.open(any(), any()) } returns embedded(BrowserSessionResult.Outcome.CLOSED, listOf(Cookie.parse(url, "a=1")!!))
        launcher.openAndWait { url.toString() }
        every { webView.open(any(), any()) } returns embedded(BrowserSessionResult.Outcome.TIMED_OUT, emptyList())
        launcher.openAndWait { url.toString() }
        every { webView.open(any(), any()) } returns embedded(BrowserSessionResult.Outcome.CLOSED, emptyList())
        launcher.openAndWait { url.toString() }

        toasts shouldContainExactly listOf(
            app.stringResource(KMR.strings.desktop_webview_cookies_saved, "example.com"),
            app.stringResource(KMR.strings.desktop_webview_timed_out, "example.com"),
            app.stringResource(KMR.strings.desktop_webview_no_cookies, "example.com"),
        )
    }

    @Test
    fun `the manual dialog state follows the open`() {
        val entry = DesktopWebView.ManualEntry("https://example.com/", "example.com", true, "UA", emptyList())
        every { webView.open(any(), any()) } returns DesktopWebView.Opened.Manual(entry)
        launcher.openAndWait { entry.url }
        launcher.state.shouldBeInstanceOf<WebViewLauncher.State.Manual>().entry shouldBe entry
    }
}
