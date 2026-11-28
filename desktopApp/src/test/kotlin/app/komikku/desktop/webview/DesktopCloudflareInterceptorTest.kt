package app.komikku.desktop.webview

import android.app.Application
import android.webkit.CookieManager
import app.komikku.desktop.DesktopServices
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserPage
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.removeBrowserCookies
import app.komikku.network.browser.storeBrowserCookies
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * The desktop Cloudflare interceptor (P7) against `MockWebServer` with a [FakeBrowserBackend]: Android's detection
 * decides, the backend's clearance cookie goes into the shared cookie jar and the request is sent once more; no backend,
 * an unsolved or failed session and stale cookies give a [CloudflareBypassException]; other 403s are left alone.
 */
class DesktopCloudflareInterceptorTest {

    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()
    private val context by lazy { Application(dir.resolve("data")) }
    private val jar = AndroidCookieJar()
    private val backend = FakeBrowserBackend()
    private var now = 1_000_000L
    private val hosts = CloudflareHostState(clock = { now })

    private fun client(backendProvider: () -> BrowserBackend = { backend }) = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(UserAgentInterceptor { DEFAULT_UA })
        .addInterceptor(CloudflareInterceptor(context, jar, { DEFAULT_UA }, backendProvider, timeout = 5.seconds, hostStateProvider = { hosts }))
        .build()

    @BeforeEach
    fun start() {
        CookieManager.getInstance().removeAllCookies(null)
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
        BrowserBackends.current = app.komikku.network.browser.NoBrowserBackend
    }

    /** Challenge until the request carries `cf_clearance=<clearance>`, then 200. */
    private fun protectedSite(clearance: String = "solved") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if ("cf_clearance=$clearance" in request.headers["Cookie"].orEmpty()) {
                    MockResponse.Builder().code(200).body("manga list").build()
                } else {
                    challenge()
                }
        }
    }

    @Test
    fun `a challenge is solved by the browser, its cookie stored and the request sent again`() {
        protectedSite()
        backend.browse = { session -> session.cookies + FakeBrowserBackend.clearance(session) }

        val request = GET(server.url("/popular").toString(), Headers.headersOf("Referer", "https://example.org/"))
        client().newCall(request).execute().use { response ->
            response.code shouldBe 200
            response.body.string() shouldBe "manga list"
        }

        backend.sessions shouldHaveSize 1
        val session = backend.sessions.single()
        session.url shouldBe server.url("/popular")
        session.userAgent shouldBe DEFAULT_UA
        session.headers["Referer"] shouldBe "https://example.org/"
        session.headers.keys.none { it.equals("User-Agent", ignoreCase = true) } shouldBe true
        session.timeout shouldBe 5.seconds
        session.interactive shouldBe false

        server.requestCount shouldBe 2
        server.takeRequest().headers["Cookie"] shouldBe null
        val retried = server.takeRequest()
        retried.headers["Cookie"] shouldBe "cf_clearance=solved"
        retried.headers["User-Agent"] shouldBe DEFAULT_UA
        jar.get(server.url("/")).map { it.name to it.value } shouldBe listOf("cf_clearance" to "solved")
    }

    @Test
    fun `the default network client uses the registered backend`() {
        protectedSite()
        backend.browse = { session -> listOf(FakeBrowserBackend.clearance(session)) }
        BrowserBackends.current = backend
        val services = DesktopServices(Application(dir.resolve("app")), DesktopPreferenceStore(dir.resolve("prefs.json")))

        try {
            services.network.client.newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 200 }

            backend.sessions shouldHaveSize 1
            backend.sessions.single().userAgent shouldBe services.network.defaultUserAgentProvider()
        } finally {
            // Its journal in `dir` stays open otherwise, and Windows does not delete an open file
            services.network.client.cache?.close()
        }
    }

    @Test
    fun `without a browser backend the request fails with an actionable error`() {
        protectedSite()
        backend.availability = BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_INSTALLED)

        val error = assertThrows<CloudflareBypassException> {
            client().newCall(GET(server.url("/").toString())).execute()
        }

        error.reason shouldBe CloudflareBypassException.Reason.NO_BROWSER
        error.host shouldBe server.hostName
        error.message.orEmpty() shouldContain server.hostName
        error.message.orEmpty() shouldContain "Open in WebView"
        backend.sessions.shouldBeEmpty()
        server.requestCount shouldBe 1
    }

    @Test
    fun `the desktop default has no backend`() {
        protectedSite()

        val error = assertThrows<CloudflareBypassException> {
            client(backendProvider = { BrowserBackends.current }).newCall(GET(server.url("/").toString())).execute()
        }
        error.reason shouldBe CloudflareBypassException.Reason.NO_BROWSER
    }

    @Test
    fun `a session that times out without a clearance fails and is not retried`() {
        protectedSite()
        backend.browse = { session -> session.cookies + Cookie.parse(session.url, "other=1")!! }

        val error = assertThrows<CloudflareBypassException> {
            client().newCall(GET(server.url("/").toString())).execute()
        }

        error.reason shouldBe CloudflareBypassException.Reason.TIMED_OUT
        error.message.orEmpty() shouldContain "Failed to bypass Cloudflare"
        server.requestCount shouldBe 1
        // What the browser got is kept, like Android's shared WebView cookie store.
        jar.get(server.url("/")).map { it.name } shouldBe listOf("other")
    }

    @Test
    fun `a closed session or a failing backend fail with their own reason`() {
        protectedSite()
        backend.unmetOutcome = BrowserSessionResult.Outcome.CLOSED
        backend.browse = { emptyList() }
        assertThrows<CloudflareBypassException> { client().newCall(GET(server.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.NOT_SOLVED

        // After the remembered failure (P7 review) the next attempt runs the browser again.
        now += CloudflareHostState.FAILURE_MEMORY.inWholeMilliseconds
        backend.browse = { throw BrowserUnavailableException("native files missing") }
        val failed = assertThrows<CloudflareBypassException> { client().newCall(GET(server.url("/").toString())).execute() }
        failed.reason shouldBe CloudflareBypassException.Reason.BROWSER_FAILED
        (failed.cause is BrowserUnavailableException) shouldBe true
    }

    @Test
    fun `a stale clearance is removed first and does not count as solved`() {
        protectedSite(clearance = "fresh")
        // A site-wide clearance the site no longer accepts (the jar's `remove` alone would miss its domain).
        val stale = Cookie.Builder().name("cf_clearance").value("stale").domain(server.hostName).path("/")
            .expiresAt(System.currentTimeMillis() + 3_600_000).build()
        jar.storeBrowserCookies(listOf(stale))
        backend.browse = { session -> session.cookies + FakeBrowserBackend.clearance(session, "stale") }

        assertThrows<CloudflareBypassException> { client().newCall(GET(server.url("/").toString())).execute() }

        server.takeRequest().headers["Cookie"] shouldBe "cf_clearance=stale"
        backend.sessions.single().cookies.none { it.name == "cf_clearance" } shouldBe true
        val page = BrowserPage(server.url("/"), 200, listOf(FakeBrowserBackend.clearance(backend.sessions.single(), "stale")))
        backend.sessions.single().until.isMet(page) shouldBe false

        // The next challenge (after the remembered failure) is solved with a fresh one.
        now += CloudflareHostState.FAILURE_MEMORY.inWholeMilliseconds
        backend.browse = { session -> session.cookies + FakeBrowserBackend.clearance(session, "fresh") }
        client().newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 200 }
    }

    @Test
    fun `the retry uses the browser's user agent when it sent another one`() {
        protectedSite()
        backend.userAgent = "Mozilla/5.0 (X11; Linux x86_64) Desktop browser"
        backend.browse = { session -> listOf(FakeBrowserBackend.clearance(session)) }

        client().newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 200 }

        server.takeRequest().headers["User-Agent"] shouldBe DEFAULT_UA
        server.takeRequest().headers["User-Agent"] shouldBe "Mozilla/5.0 (X11; Linux x86_64) Desktop browser"
    }

    @Test
    fun `a 403 that is no challenge is returned untouched`() {
        // Cloudflare, but no challenge markers (a geo block), and a challenge page from another server.
        server.enqueue(MockResponse.Builder().code(403).addHeader("Server", "cloudflare").body("<html>Access denied</html>").build())
        server.enqueue(MockResponse.Builder().code(403).addHeader("Server", "nginx").body(CHALLENGE_BODY).build())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Server", "cloudflare").body(CHALLENGE_BODY).build())

        repeat(3) {
            client().newCall(GET(server.url("/").toString())).execute().use { response ->
                (response.code == 403 || response.code == 429) shouldBe true
            }
        }

        backend.sessions.shouldBeEmpty()
        server.requestCount shouldBe 3
    }

    @Test
    fun `parallel challenges of one host open one browser`() {
        protectedSite()
        val started = CountDownLatch(1)
        backend.browse = { session ->
            started.countDown()
            Thread.sleep(300)
            listOf(FakeBrowserBackend.clearance(session))
        }
        val client = client()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val calls = (1..3).map { pool.submit<Int> { client.newCall(GET(server.url("/$it").toString())).execute().use { r -> r.code } } }
            calls.map { it.get(10, TimeUnit.SECONDS) } shouldBe listOf(200, 200, 200)
        } finally {
            pool.shutdownNow()
        }
        backend.sessions shouldHaveSize 1
    }

    @Test
    fun `a failed solve is not repeated by the requests behind it and right after it`() {
        protectedSite()
        val started = CountDownLatch(1)
        backend.browse = { session ->
            started.countDown()
            Thread.sleep(300)
            session.cookies
        }
        val client = client()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val calls = (1..3).map {
                pool.submit<CloudflareBypassException.Reason> {
                    assertThrows<CloudflareBypassException> { client.newCall(GET(server.url("/$it").toString())).execute() }.reason
                }
            }
            calls.map { it.get(10, TimeUnit.SECONDS) } shouldBe List(3) { CloudflareBypassException.Reason.TIMED_OUT }
        } finally {
            pool.shutdownNow()
        }
        backend.sessions shouldHaveSize 1

        // A request right after it fails at once with the same reason, without a browser run.
        assertThrows<CloudflareBypassException> { client.newCall(GET(server.url("/next").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.TIMED_OUT
        backend.sessions shouldHaveSize 1

        // A manual save (cleared failure) lets the next one try again at once.
        hosts.clearFailure(server.hostName)
        assertThrows<CloudflareBypassException> { client.newCall(GET(server.url("/again").toString())).execute() }
        backend.sessions shouldHaveSize 2

        // So does a new clearance in the jar (pasted by hand): the request is sent with it.
        jar.storeBrowserCookies(listOf(FakeBrowserBackend.clearance(backend.sessions.last())))
        client.newCall(GET(server.url("/pasted").toString())).execute().use { it.code shouldBe 200 }
        backend.sessions shouldHaveSize 2

        // Without it the last failure still counts, until the memory ends.
        jar.removeBrowserCookies(server.url("/"))
        assertThrows<CloudflareBypassException> { client.newCall(GET(server.url("/x").toString())).execute() }
        backend.sessions shouldHaveSize 2
        now += CloudflareHostState.FAILURE_MEMORY.inWholeMilliseconds
        assertThrows<CloudflareBypassException> { client.newCall(GET(server.url("/y").toString())).execute() }
        backend.sessions shouldHaveSize 3
    }

    @Test
    fun `the solver's user agent is used for the waiters and the host's later requests`() {
        // Like Cloudflare: the clearance only works with the user agent that got it.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if ("cf_clearance=solved" in request.headers["Cookie"].orEmpty() && request.headers["User-Agent"] == BROWSER_UA) {
                    MockResponse.Builder().code(200).body("ok").build()
                } else {
                    challenge()
                }
        }
        backend.userAgent = BROWSER_UA
        backend.browse = { session ->
            Thread.sleep(300)
            listOf(FakeBrowserBackend.clearance(session))
        }
        val client = client()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val calls = (1..3).map { pool.submit<Int> { client.newCall(GET(server.url("/$it").toString())).execute().use { r -> r.code } } }
            calls.map { it.get(10, TimeUnit.SECONDS) } shouldBe listOf(200, 200, 200)
        } finally {
            pool.shutdownNow()
        }
        backend.sessions shouldHaveSize 1
        hosts.userAgentOverride(server.hostName)?.userAgent shouldBe BROWSER_UA

        // A later request of the source (its own User-Agent header) goes through at once with the browser's.
        val before = server.requestCount
        val request = GET(server.url("/later").toString(), Headers.headersOf("User-Agent", "Source/1.0"))
        client.newCall(request).execute().use { it.code shouldBe 200 }
        server.requestCount shouldBe before + 1

        // Once that clearance is replaced, the source's own User-Agent is sent again.
        jar.removeBrowserCookies(server.url("/"))
        jar.storeBrowserCookies(listOf(FakeBrowserBackend.clearance(backend.sessions.single(), "other")))
        backend.availability = BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_INSTALLED)
        assertThrows<CloudflareBypassException> { client.newCall(request).execute() }
        (1..server.requestCount).map { server.takeRequest() }.last().headers["User-Agent"] shouldBe "Source/1.0"
    }

    @Test
    fun `a challenge on a redirect target is solved for that address`() {
        // The entry address redirects to another host (127.0.0.1 instead of localhost), which is protected.
        val target = server.url("/new").newBuilder().host(if (server.hostName == "127.0.0.1") "localhost" else "127.0.0.1").build()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/old" -> MockResponse.Builder().code(302).addHeader("Location", target.toString()).build()
                "cf_clearance=solved" in request.headers["Cookie"].orEmpty() -> MockResponse.Builder().code(200).body("ok").build()
                else -> challenge()
            }
        }
        // The first host has a clearance of its own, which the challenged request did not carry.
        jar.storeBrowserCookies(listOf(FakeBrowserBackend.clearance(sessionFor(server.url("/")), "first-host")))
        backend.browse = { session -> listOf(FakeBrowserBackend.clearance(session)) }

        client().newCall(GET(server.url("/old").toString())).execute().use { response ->
            response.code shouldBe 200
            response.body.string() shouldBe "ok"
        }

        backend.sessions.single().url shouldBe target
        jar.get(target).map { it.name to it.value } shouldBe listOf("cf_clearance" to "solved")
        jar.get(server.url("/")).map { it.value } shouldBe listOf("first-host")
    }

    @Test
    fun `a clearance reached through a redirect is resent with its user agent instead of solved again`() {
        val target = server.url("/new").newBuilder().host(if (server.hostName == "127.0.0.1") "localhost" else "127.0.0.1").build()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/old" -> MockResponse.Builder().code(302).addHeader("Location", target.toString()).build()
                "cf_clearance=solved" in request.headers["Cookie"].orEmpty() && request.headers["User-Agent"] == BROWSER_UA ->
                    MockResponse.Builder().code(200).body("ok").build()
                else -> challenge()
            }
        }
        // The target was solved earlier with the browser's User-Agent; the first host has no override of its own.
        jar.storeBrowserCookies(listOf(FakeBrowserBackend.clearance(sessionFor(target))))
        hosts.setUserAgent(target.host, BROWSER_UA, "solved")

        client().newCall(GET(server.url("/old").toString())).execute().use { it.code shouldBe 200 }

        backend.sessions.shouldBeEmpty()
    }

    private fun sessionFor(url: okhttp3.HttpUrl) = app.komikku.network.browser.BrowserSession(
        url = url,
        userAgent = DEFAULT_UA,
        until = BrowserCondition.UntilClosed,
        timeout = 1.seconds,
    )

    @Test
    fun `cookie and url conditions`() {
        val url = server.url("/a")
        val cookie = Cookie.parse(url, "cf_clearance=new")!!
        BrowserCondition.cookieSet(url, "cf_clearance").isMet(BrowserPage(url, 200, listOf(cookie))) shouldBe true
        BrowserCondition.cookieSet(url, "cf_clearance", notValue = "new").isMet(BrowserPage(url, 200, listOf(cookie))) shouldBe false
        BrowserCondition.cookieSet(url, "cf_clearance").isMet(BrowserPage(url, 200, emptyList())) shouldBe false
        BrowserCondition.urlReached { it.encodedPath == "/done" }.isMet(BrowserPage(server.url("/done"), 200, emptyList())) shouldBe true
        BrowserCondition.UntilClosed.isMet(BrowserPage(url, 200, listOf(cookie))) shouldBe false
    }

    private fun challenge() = MockResponse.Builder()
        .code(403)
        .addHeader("Server", "cloudflare")
        .body(CHALLENGE_BODY)
        .build()

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
        const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) Desktop browser"
        const val CHALLENGE_BODY = "<html><body><h1 id=\"challenge-error-title\">Just a moment...</h1></body></html>"
    }
}
