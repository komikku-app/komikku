package app.komikku.desktop.webview

import android.app.Application
import android.webkit.CookieManager
import app.komikku.desktop.DesktopServices
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import app.komikku.network.browser.NoBrowserBackend
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.domain.source.service.SourceManager
import java.io.File

/**
 * The desktop "Open in WebView" (P7): without a browser backend the page opens in the system browser and pasted cookies
 * and user agent go into the app's cookie jar and `default_user_agent`, so a Cloudflare check can be passed by hand;
 * with a backend an interactive session runs and its cookies are stored. Also [ManualCookies]' accepted forms.
 */
class DesktopWebViewTest {

    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()
    private val services by lazy {
        DesktopServices(Application(dir.resolve("data")), DesktopPreferenceStore(dir.resolve("preferences.json")))
    }
    private val source = mockk<HttpSource> {
        every { id } returns SOURCE_ID
        every { headers } returns Headers.headersOf("User-Agent", SOURCE_UA, "Referer", "https://example.org/", "Host", "x")
    }
    private val sourceManager = mockk<SourceManager> {
        every { get(SOURCE_ID) } returns source
        every { get(neq(SOURCE_ID)) } returns null
    }
    private val opened = mutableListOf<String>()
    private val backend = FakeBrowserBackend()
    private var browserOpens = true

    private fun webView(withBackend: Boolean = false) = DesktopWebView(
        network = services.network,
        networkPreferences = services.networkPreferences,
        sourceManager = sourceManager,
        backendProvider = { if (withBackend) backend else BrowserBackends.current },
        browserOpener = {
            opened += it
            browserOpens
        },
        clock = { NOW },
    )

    @BeforeEach
    fun start() {
        CookieManager.getInstance().removeAllCookies(null)
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
        BrowserBackends.current = NoBrowserBackend
    }

    @Test
    fun `without a backend the page opens in the system browser with the source's user agent`() {
        val url = server.url("/manga/1").toString()
        services.network.cookieJar.saveFromResponse(url.toHttpUrl(), listOf(Cookie.parse(url.toHttpUrl(), "session=1")!!))

        val result = webView().open(url, SOURCE_ID)

        val entry = result.shouldBeInstanceOf<DesktopWebView.Opened.Manual>().entry
        opened shouldContainExactly listOf(url)
        entry.browserOpened shouldBe true
        entry.host shouldBe server.hostName
        entry.userAgent shouldBe SOURCE_UA
        entry.storedCookies shouldContainExactly listOf("session")

        // Without a source (or one without a User-Agent header) the app's default.
        browserOpens = false
        val plain = webView().open(url, sourceId = 99L).shouldBeInstanceOf<DesktopWebView.Opened.Manual>().entry
        plain.userAgent shouldBe services.network.defaultUserAgentProvider()
        plain.browserOpened shouldBe false

        webView().open("not a url", SOURCE_ID).shouldBeInstanceOf<DesktopWebView.Opened.InvalidUrl>()
    }

    @Test
    fun `pasted cookies and user agent get a request past the Cloudflare check`() {
        // The site lets requests with the clearance and the browser's user agent through, like Cloudflare.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cookie = request.headers["Cookie"].orEmpty()
                return if ("cf_clearance=abc" in cookie && request.headers["User-Agent"] == BROWSER_UA) {
                    MockResponse.Builder().code(200).body("ok").build()
                } else {
                    MockResponse.Builder().code(403).addHeader("Server", "cloudflare")
                        .body(DesktopCloudflareInterceptorTest.CHALLENGE_BODY).build()
                }
            }
        }
        val url = server.url("/popular").toString()
        val client = services.network.client

        // No backend: an actionable error instead of the challenge page.
        assertThrows<CloudflareBypassException> { client.newCall(GET(url)).execute() }
            .reason shouldBe CloudflareBypassException.Reason.NO_BROWSER

        val webView = webView()
        val entry = webView.open(url, sourceId = null).shouldBeInstanceOf<DesktopWebView.Opened.Manual>().entry
        val saved = webView.saveManual(entry, "Cookie: cf_clearance=abc; theme=dark", " $BROWSER_UA ")

        saved shouldBe DesktopWebView.SaveResult.Saved(cookies = 2, userAgentChanged = true)
        services.networkPreferences.defaultUserAgent().get() shouldBe BROWSER_UA
        client.newCall(GET(url)).execute().use { response ->
            response.code shouldBe 200
            response.body.string() shouldBe "ok"
        }
        val passed = (1..server.requestCount).map { server.takeRequest() }.last()
        passed.headers["Cookie"] shouldBe "cf_clearance=abc; theme=dark"
        passed.headers["User-Agent"] shouldBe BROWSER_UA

        // Pasting again replaces the value; the same user agent is no change.
        webView.saveManual(entry.copy(userAgent = BROWSER_UA), "cf_clearance=def", BROWSER_UA) shouldBe
            DesktopWebView.SaveResult.Saved(cookies = 1, userAgentChanged = false)
        services.network.cookieJar.get(url.toHttpUrl()).map { it.name to it.value } shouldContainExactlyInAnyOrder
            listOf("cf_clearance" to "def", "theme" to "dark")
    }

    @Test
    fun `invalid input stores nothing`() {
        val url = server.url("/").toString()
        val webView = webView()
        val entry = webView.open(url, SOURCE_ID).shouldBeInstanceOf<DesktopWebView.Opened.Manual>().entry
        val uaBefore = services.networkPreferences.defaultUserAgent().get()

        webView.saveManual(entry, "cf_clearance=abc; broken", BROWSER_UA) shouldBe DesktopWebView.SaveResult.InvalidCookie("broken")
        webView.saveManual(entry, "cf_clearance=abc", "  ") shouldBe DesktopWebView.SaveResult.UserAgentBlank
        webView.saveManual(entry, "cf_clearance=abc", "Mozilla\u0007") shouldBe DesktopWebView.SaveResult.UserAgentInvalid

        services.network.cookieJar.get(url.toHttpUrl()).shouldBeEmpty()
        services.networkPreferences.defaultUserAgent().get() shouldBe uaBefore
    }

    @Test
    fun `clear cookies removes the site's cookies, site-wide ones included`() {
        val url = "https://www.example.com/manga/1"
        val webView = webView()
        val entry = webView.open(url, SOURCE_ID).shouldBeInstanceOf<DesktopWebView.Opened.Manual>().entry
        webView.saveManual(entry, "cf_clearance=abc; session=1", entry.userAgent)
        val other = "https://other.org/".toHttpUrl()
        services.network.cookieJar.saveFromResponse(other, listOf(Cookie.parse(other, "keep=1")!!))
        // Stored for the registrable domain: other hosts of the site get them too.
        services.network.cookieJar.get("https://api.example.com/".toHttpUrl()).map { it.name } shouldContainExactly
            listOf("cf_clearance", "session")

        webView.clearCookies(url) shouldBe 2

        services.network.cookieJar.get(url.toHttpUrl()).shouldBeEmpty()
        services.network.cookieJar.get(other).map { it.name } shouldContainExactly listOf("keep")
    }

    @Test
    fun `with a backend an interactive session runs and its cookies are stored`() {
        val url = server.url("/manga/1")
        backend.unmetOutcome = BrowserSessionResult.Outcome.CLOSED
        backend.browse = { session -> session.cookies + Cookie.parse(session.url, "login=yes")!! }

        val result = webView(withBackend = true).open(url.toString(), SOURCE_ID)

        result.shouldBeInstanceOf<DesktopWebView.Opened.Embedded>().result.outcome shouldBe BrowserSessionResult.Outcome.CLOSED
        opened.shouldBeEmpty()
        val session = backend.sessions.single()
        session.interactive shouldBe true
        session.until shouldBe BrowserCondition.UntilClosed
        session.userAgent shouldBe SOURCE_UA
        session.headers shouldBe mapOf("Referer" to "https://example.org/")
        session.timeout shouldBe DesktopWebView.INTERACTIVE_TIMEOUT
        services.network.cookieJar.get(url).map { it.name } shouldContainExactly listOf("login")
    }

    @Test
    fun `an unavailable or failing backend falls back to the system browser`() {
        val url = server.url("/").toString()
        backend.availability = BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_SUPPORTED, "no display")
        webView(withBackend = true).open(url, SOURCE_ID).shouldBeInstanceOf<DesktopWebView.Opened.Manual>()
        backend.sessions.shouldBeEmpty()

        backend.availability = BrowserAvailability.Available
        backend.browse = { throw BrowserUnavailableException("native files missing") }
        webView(withBackend = true).open(url, SOURCE_ID).shouldBeInstanceOf<DesktopWebView.Opened.Manual>()
        opened shouldContainExactly listOf(url, url)
    }

    @Test
    fun `manual cookies accept headers, Set-Cookie lines and cookie table rows`() {
        ManualCookies.parse("Cookie: a=1; b=2") shouldBe ManualCookies.Parsed.Cookies(listOf("a" to "1", "b" to "2"))
        ManualCookies.parse("cf_clearance=x.y-z; Path=/; Domain=.example.com; Secure; HttpOnly; SameSite=None") shouldBe
            ManualCookies.Parsed.Cookies(listOf("cf_clearance" to "x.y-z"))
        ManualCookies.parse("Name\tValue\tDomain\nsession\tabc\t.example.com\t/\n\ncf_clearance\tq=w\t.example.com") shouldBe
            ManualCookies.Parsed.Cookies(listOf("session" to "abc", "cf_clearance" to "q=w"))
        ManualCookies.parse("a=1\na=2; c=\"quoted\"") shouldBe ManualCookies.Parsed.Cookies(listOf("a" to "2", "c" to "quoted"))
        ManualCookies.parse("  ") shouldBe ManualCookies.Parsed.Cookies(emptyList())
        ManualCookies.parse("a=1; just text") shouldBe ManualCookies.Parsed.Invalid("just text")
        ManualCookies.parse("bad name=1") shouldBe ManualCookies.Parsed.Invalid("bad name=1")
        ManualCookies.parse("a=1,2") shouldBe ManualCookies.Parsed.Invalid("a=1,2")

        val cookies = ManualCookies.toCookies("https://www.example.com/x".toHttpUrl(), listOf("a" to "1"), NOW)
        cookies.single().domain shouldBe "example.com"
        cookies.single().hostOnly shouldBe false
        cookies.single().secure shouldBe true
        cookies.single().path shouldBe "/"
        cookies.single().expiresAt shouldBe NOW + ManualCookies.LIFETIME.inWholeMilliseconds
        val local = ManualCookies.toCookies("http://127.0.0.1:8080/".toHttpUrl(), listOf("a" to "1"), NOW).single()
        local.hostOnly shouldBe true
        local.secure shouldBe false
    }

    companion object {
        const val SOURCE_ID = 42L
        const val SOURCE_UA = "Mozilla/5.0 (source)"
        const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"
        val NOW = System.currentTimeMillis()
    }
}
