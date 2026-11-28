package app.komikku.desktop.webview

import android.app.Application
import android.webkit.CookieManager
import app.komikku.desktop.DesktopServices
import app.komikku.desktop.ui.more.flareSolverrTestMessage
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.NoBrowserBackend
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * P8: the FlareSolverr-compatible backend, with one `MockWebServer` as the protected site and another standing in for
 * the solver: a solved challenge's cookies and User-Agent get the retry through; error answers, an unreachable
 * service, timeouts and a missing address give clear errors; "Test connection".
 */
class FlareSolverrBackendTest {

    @TempDir
    lateinit var dir: File

    private val site = MockWebServer()
    private val solver = MockWebServer()
    private val context by lazy { Application(dir.resolve("data")) }
    private val jar = AndroidCookieJar()
    private var endpoint = ""
    private val hosts = CloudflareHostState()

    private fun backend(
        minTimeout: kotlin.time.Duration = 1.seconds,
        answerMargin: kotlin.time.Duration = 5.seconds,
        client: OkHttpClient? = null,
    ) = if (client == null) {
        FlareSolverrBackend({ endpoint }, minTimeout = minTimeout, answerMargin = answerMargin, onEndpointChanged = { hosts.clearFailures() })
    } else {
        FlareSolverrBackend({ endpoint }, client, minTimeout, answerMargin, onEndpointChanged = { hosts.clearFailures() })
    }

    private fun session(until: BrowserCondition = BrowserCondition.cookieSet(site.url("/"), "cf_clearance")) =
        BrowserSession(url = site.url("/"), userAgent = SOURCE_UA, until = until, timeout = 1.seconds)

    private fun answer(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    private fun client(backend: FlareSolverrBackend = backend()) = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(UserAgentInterceptor { SOURCE_UA })
        .addInterceptor(CloudflareInterceptor(context, jar, { SOURCE_UA }, { backend }, timeout = 2.seconds, hostStateProvider = { hosts }))
        .build()

    @BeforeEach
    fun start() {
        CookieManager.getInstance().removeAllCookies(null)
        site.start()
        solver.start()
        endpoint = solver.url("/").toString()
        // Like Cloudflare: the clearance works only with the browser's User-Agent.
        site.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if ("cf_clearance=solved" in request.headers["Cookie"].orEmpty() && request.headers["User-Agent"] == SOLVER_UA) {
                    MockResponse.Builder().code(200).body("manga list").build()
                } else {
                    MockResponse.Builder().code(403).addHeader("Server", "cloudflare").addHeader("cf-mitigated", "challenge")
                        .body(DesktopCloudflareInterceptorTest.CHALLENGE_BODY).build()
                }
        }
    }

    @AfterEach
    fun stop() {
        site.close()
        solver.close()
        BrowserBackends.current = NoBrowserBackend
    }

    private fun solved(cookieDomain: String = site.hostName) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(
            """
            {"status": "ok", "message": "Challenge solved!",
             "solution": {"url": "${site.url("/popular")}", "status": 200,
               "cookies": [
                 {"name": "cf_clearance", "value": "solved", "domain": "$cookieDomain", "path": "/",
                  "expires": ${System.currentTimeMillis() / 1000 + 3600}.5, "httpOnly": true, "secure": false},
                 {"name": "session", "value": "s1", "domain": "$cookieDomain", "path": "/", "expires": -1}
               ],
               "userAgent": "$SOLVER_UA", "headers": {}, "response": "<html></html>"},
             "startTimestamp": 1, "endTimestamp": 2, "version": "3.3.21"}
            """.trimIndent(),
        )
        .build()

    @Test
    fun `a solved challenge's cookies and user agent get the retry through`() {
        solver.enqueue(solved())

        client().newCall(GET(site.url("/popular").toString())).execute().use { response ->
            response.code shouldBe 200
            response.body.string() shouldBe "manga list"
        }

        val call = solver.takeRequest()
        call.method shouldBe "POST"
        call.url.encodedPath shouldBe "/v1"
        val body = Json.parseToJsonElement(call.body!!.utf8()).jsonObject
        body["cmd"]!!.jsonPrimitive.content shouldBe "request.get"
        body["url"]!!.jsonPrimitive.content shouldBe site.url("/popular").toString()
        // The session's 2 s (above the backend's 1 s minimum here).
        body["maxTimeout"]!!.jsonPrimitive.long shouldBe 2_000L

        site.requestCount shouldBe 2
        site.takeRequest()
        val retried = site.takeRequest()
        retried.headers["Cookie"] shouldBe "cf_clearance=solved; session=s1"
        retried.headers["User-Agent"] shouldBe SOLVER_UA
        jar.get(site.url("/")).map { it.name } shouldBe listOf("cf_clearance", "session")

        // The host's next request goes straight through with the solver's User-Agent (no second solve).
        client().newCall(GET(site.url("/next").toString())).execute().use { it.code shouldBe 200 }
        solver.requestCount shouldBe 1
    }

    @Test
    fun `the jar's cookies go to the solver and a domain cookie keeps its domain`() {
        jar.saveFromResponse(site.url("/"), listOf(okhttp3.Cookie.parse(site.url("/"), "login=yes")!!))
        solver.enqueue(solved(cookieDomain = ".${site.hostName}"))
        val backend = backend()

        val result = backend.run(
            app.komikku.network.browser.BrowserSession(
                url = site.url("/popular"),
                userAgent = SOURCE_UA,
                cookies = jar.get(site.url("/")),
                until = app.komikku.network.browser.BrowserCondition.cookieSet(site.url("/popular"), "cf_clearance"),
                timeout = 1.seconds,
            ),
        )

        result.outcome shouldBe app.komikku.network.browser.BrowserSessionResult.Outcome.CONDITION_MET
        result.userAgent shouldBe SOLVER_UA
        result.cookies.first().hostOnly shouldBe false
        result.cookies.first().persistent shouldBe true
        result.cookies.last().persistent shouldBe false
        val sent = Json.parseToJsonElement(solver.takeRequest().body!!.utf8()).jsonObject["cookies"]!!.jsonArray
        sent.single().jsonObject["name"]!!.jsonPrimitive.content shouldBe "login"
        // 1 s session, raised to the 1 s minimum: unchanged; a longer minimum wins.
        solver.enqueue(solved())
        backend(minTimeout = 60.seconds).run(
            app.komikku.network.browser.BrowserSession(
                url = site.url("/"),
                userAgent = SOURCE_UA,
                until = app.komikku.network.browser.BrowserCondition.UntilClosed,
                timeout = 1.seconds,
            ),
        ).outcome shouldBe app.komikku.network.browser.BrowserSessionResult.Outcome.CLOSED
        Json.parseToJsonElement(solver.takeRequest().body!!.utf8()).jsonObject["maxTimeout"]!!.jsonPrimitive.long shouldBe 60_000L
    }

    @Test
    fun `an error answer fails with the solver's message`() {
        solver.enqueue(
            MockResponse.Builder().code(500)
                .body("""{"status": "error", "message": "Error: Cloudflare has blocked this request.", "version": "3.3.21"}""")
                .build(),
        )

        val error = assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }

        error.reason shouldBe CloudflareBypassException.Reason.BROWSER_FAILED
        error.message.orEmpty() shouldContain "Cloudflare has blocked this request"
        error.message.orEmpty() shouldContain site.hostName
        // P8 review: the reason is typed, the text comes from the strings, and the message's own period is not doubled.
        val cause = error.cause.shouldBeInstanceOf<FlareSolverrException>()
        cause.reason shouldBe FlareSolverrException.Reason.ERROR_ANSWER
        cause.httpCode shouldBe 500
        cause.detail shouldBe "Error: Cloudflare has blocked this request."
        cause.message shouldBe stringResource(KMR.strings.desktop_flaresolverr_error_answer, 500, cause.detail!!)
        error.message.orEmpty() shouldNotContain ".."
        site.requestCount shouldBe 1
    }

    @Test
    fun `an unreachable solver fails clearly, without the address' credentials`() {
        val closed = MockWebServer().apply { start() }
        val port = closed.port
        closed.close()
        endpoint = "http://user:secret@localhost:$port/"

        val error = assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }

        error.reason shouldBe CloudflareBypassException.Reason.BROWSER_FAILED
        error.message.orEmpty() shouldContain "unreachable"
        error.message.orEmpty() shouldNotContain "secret"
        (error.cause is BrowserUnavailableException) shouldBe true
    }

    @Test
    fun `a timeout of the solver or of its answer is a timeout`() {
        solver.enqueue(
            MockResponse.Builder().code(500)
                .body("""{"status": "error", "message": "Error: Error solving the challenge. Timeout after 2.0 seconds."}""")
                .build(),
        )
        assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.TIMED_OUT

        // No answer in time: the HTTP call gives up after the solve time plus the margin.
        hosts.clearFailure(site.hostName)
        solver.enqueue(MockResponse.Builder().code(200).body(solvedBodyLater()).headersDelay(5, TimeUnit.SECONDS).build())
        val slow = backend(minTimeout = 100.milliseconds, answerMargin = 200.milliseconds)
        val started = System.nanoTime()
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor(CloudflareInterceptor(context, jar, { SOURCE_UA }, { slow }, timeout = 100.milliseconds, hostStateProvider = { hosts }))
            .build()
        assertThrows<CloudflareBypassException> { client.newCall(GET(site.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.TIMED_OUT
        ((System.nanoTime() - started) / 1_000_000 < 4_000) shouldBe true
    }

    @Test
    fun `without an address the manual flow's error stays`() {
        endpoint = ""
        val error = assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }
        error.reason shouldBe CloudflareBypassException.Reason.NO_BROWSER
        error.message.orEmpty() shouldContain "Open in WebView"
        solver.requestCount shouldBe 0

        endpoint = "ftp://localhost:8191"
        assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.NO_BROWSER
        solver.requestCount shouldBe 0
    }

    @Test
    fun `the app installs it from the setting and Open in WebView keeps the manual flow`() {
        val services = DesktopServices(Application(dir.resolve("app")), DesktopPreferenceStore(dir.resolve("prefs.json")))
        val preferences = DesktopCloudflarePreferences(services.preferenceStore)
        val backend = DesktopCloudflare.installBackend(preferences)
        BrowserBackends.current shouldBe backend
        backend.availability().shouldBeInstanceOf<app.komikku.network.browser.BrowserAvailability.Unavailable>()

        preferences.flareSolverrUrl().set(solver.url("/v1").toString())
        backend.availability() shouldBe app.komikku.network.browser.BrowserAvailability.Available
        solver.enqueue(solved())
        try {
            services.network.client.newCall(GET(site.url("/popular").toString())).execute().use { it.code shouldBe 200 }

            // It shows no pages: "Open in WebView" opens the system browser and the cookie dialog.
            val webView = DesktopWebView(services.network, services.networkPreferences, io.mockk.mockk(relaxed = true), browserOpener = { true })
            webView.open(site.url("/").toString(), null).shouldBeInstanceOf<DesktopWebView.Opened.Manual>()
            solver.requestCount shouldBe 1
        } finally {
            // Its journal in `dir` stays open otherwise, and Windows does not delete an open file
            services.network.client.cache?.close()
        }
    }

    @Test
    fun `addresses are normalised to the API and only http(s) is accepted`() {
        FlareSolverrBackend.apiUrl("http://localhost:8191").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl("http://localhost:8191/").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl(" https://solver.lan/flare/v1?x=1 ").toString() shouldBe "https://solver.lan/flare/v1"
        FlareSolverrBackend.apiUrl("https://solver.lan/flare/").toString() shouldBe "https://solver.lan/flare/v1"
        // P8 review: a trailing slash after v1 (or doubled ones) no longer leaves an address that never works.
        FlareSolverrBackend.apiUrl("http://localhost:8191/v1").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl("http://localhost:8191/v1/").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl("http://localhost:8191//v1//").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl("https://solver.lan/flare/v1/").toString() shouldBe "https://solver.lan/flare/v1"
        FlareSolverrBackend.apiUrl("https://solver.lan/a%20b/").toString() shouldBe "https://solver.lan/a%20b/v1"
        FlareSolverrBackend.baseUrl("http://localhost:8191").toString() shouldBe "http://localhost:8191/"
        FlareSolverrBackend.baseUrl("http://localhost:8191/").toString() shouldBe "http://localhost:8191/"
        FlareSolverrBackend.baseUrl("http://localhost:8191/v1").toString() shouldBe "http://localhost:8191/"
        FlareSolverrBackend.baseUrl("http://localhost:8191/v1/").toString() shouldBe "http://localhost:8191/"
        FlareSolverrBackend.baseUrl("https://solver.lan/flare").toString() shouldBe "https://solver.lan/flare/"
        FlareSolverrBackend.baseUrl("https://solver.lan/flare/v1/").toString() shouldBe "https://solver.lan/flare/"
        FlareSolverrBackend.apiUrl("ftp://localhost") shouldBe null
        FlareSolverrBackend.apiUrl("localhost:8191") shouldBe null
        DesktopCloudflarePreferences.isValidFlareSolverrUrl("") shouldBe true
        DesktopCloudflarePreferences.isValidFlareSolverrUrl("http://localhost:8191") shouldBe true
        DesktopCloudflarePreferences.isValidFlareSolverrUrl("file:///etc/passwd") shouldBe false
    }

    @Test
    fun `test connection`() {
        solver.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"msg": "FlareSolverr is ready!", "version": "3.3.21", "userAgent": "$SOLVER_UA"}""").build(),
        )
        val backend = backend()
        val ok = backend.testConnection()
        ok shouldBe FlareSolverrBackend.ConnectionTest.Ok("3.3.21")
        solver.takeRequest().url.encodedPath shouldBe "/"
        flareSolverrTestMessage(context, ok) shouldBe context.stringResource(KMR.strings.desktop_flaresolverr_ok, "3.3.21")

        solver.enqueue(MockResponse.Builder().code(404).body("<html>not here</html>").build())
        backend.testConnection().shouldBeInstanceOf<FlareSolverrBackend.ConnectionTest.Failed>().detail shouldContain "404"

        endpoint = ""
        backend.testConnection() shouldBe FlareSolverrBackend.ConnectionTest.NotConfigured
        endpoint = "ftp://x"
        backend.testConnection() shouldBe FlareSolverrBackend.ConnectionTest.InvalidAddress
        val closed = MockWebServer().apply { start() }
        endpoint = "http://localhost:${closed.port}"
        closed.close()
        val unreachable = backend.testConnection().shouldBeInstanceOf<FlareSolverrBackend.ConnectionTest.Unreachable>()
        flareSolverrTestMessage(context, unreachable) shouldContain "unreachable"
    }

    @Test
    fun `cookie expiry is read from expires or expiry, in whole or fractional seconds`() {
        val inAnHour = System.currentTimeMillis() / 1000 + 3600
        val host = site.hostName
        solver.enqueue(
            answer(
                """
                {"status": "ok", "solution": {"url": "${site.url("/")}", "cookies": [
                  {"name": "cf_clearance", "value": "a", "domain": "$host", "path": "/", "expiry": $inAnHour},
                  {"name": "b", "value": "b", "domain": "$host", "path": "/", "expiry": $inAnHour.25},
                  {"name": "c", "value": "c", "domain": "$host", "path": "/", "expires": $inAnHour},
                  {"name": "d", "value": "d", "domain": "$host", "path": "/"},
                  {"name": "e", "value": "e", "domain": "$host", "path": "/", "expires": -1, "expiry": 0}
                ]}}
                """.trimIndent(),
            ),
        )

        val cookies = backend().run(session()).cookies.associateBy { it.name }

        // FlareSolverr v3 (Selenium) says `expiry`: the clearance is kept across a restart.
        cookies.getValue("cf_clearance").persistent shouldBe true
        cookies.getValue("cf_clearance").expiresAt shouldBe inAnHour * 1000
        cookies.getValue("b").expiresAt shouldBe inAnHour * 1000 + 250
        cookies.getValue("c").expiresAt shouldBe inAnHour * 1000
        cookies.getValue("d").persistent shouldBe false
        cookies.getValue("e").persistent shouldBe false
    }

    @Test
    fun `a connect timeout is an unreachable solver, not a timed-out solve`() {
        // Every connection attempt times out while connecting, like a firewalled or wrong address.
        val timingOut = OkHttpClient.Builder().socketFactory(ConnectTimeoutSocketFactory).build()

        val error = assertThrows<CloudflareBypassException> {
            client(backend(client = timingOut)).newCall(GET(site.url("/").toString())).execute()
        }

        error.reason shouldBe CloudflareBypassException.Reason.BROWSER_FAILED
        val cause = error.cause.shouldBeInstanceOf<FlareSolverrException>()
        cause.reason shouldBe FlareSolverrException.Reason.UNREACHABLE
        // The connect timeout itself (an InterruptedIOException, which used to count as a timed-out solve).
        cause.cause.shouldBeInstanceOf<SocketTimeoutException>()
        error.message.orEmpty() shouldContain "unreachable"
        // Not remembered: a corrected address works with the next request.
        hosts.recentFailure(site.hostName, null) shouldBe null
    }

    @Test
    fun `a solver that cannot be used is not remembered, and another address forgets the failed solves`() {
        val closed = MockWebServer().apply { start() }
        endpoint = "http://localhost:${closed.port}"
        closed.close()
        assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }
        hosts.recentFailure(site.hostName, null) shouldBe null

        // The service's own error answer is not remembered either.
        endpoint = solver.url("/").toString()
        solver.enqueue(MockResponse.Builder().code(500).body("""{"status": "error", "message": "Error: Chrome crashed"}""").build())
        assertThrows<CloudflareBypassException> { client().newCall(GET(site.url("/").toString())).execute() }
        hosts.recentFailure(site.hostName, null) shouldBe null

        // A timed-out solve is remembered: the next request fails at once, without the solver …
        solver.enqueue(MockResponse.Builder().code(500).body("""{"status": "error", "message": "Error solving the challenge. Timeout after 2.0 seconds."}""").build())
        val backend = backend()
        assertThrows<CloudflareBypassException> { client(backend).newCall(GET(site.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.TIMED_OUT
        hosts.recentFailure(site.hostName, null) shouldNotBe null
        assertThrows<CloudflareBypassException> { client(backend).newCall(GET(site.url("/").toString())).execute() }
            .reason shouldBe CloudflareBypassException.Reason.TIMED_OUT
        solver.requestCount shouldBe 2

        // … until the address changes.
        endpoint = solver.url("/v1").toString()
        solver.enqueue(solved())
        client(backend).newCall(GET(site.url("/popular").toString())).execute().use { it.code shouldBe 200 }
        solver.requestCount shouldBe 3
    }

    @Test
    fun `a cancelled request cancels the solver call and frees the host`() {
        solver.enqueue(answer(solvedBodyLater()).newBuilder().headersDelay(10, TimeUnit.SECONDS).build())
        val call = client().newCall(GET(site.url("/").toString()))
        val outcome = CompletableFuture<Throwable?>()
        thread { outcome.complete(runCatching { call.execute().close() }.exceptionOrNull()) }
        solver.takeRequest(5, TimeUnit.SECONDS).shouldNotBeNull()

        val cancelledAt = System.nanoTime()
        call.cancel()
        val error = outcome.get(5, TimeUnit.SECONDS)

        ((System.nanoTime() - cancelledAt) / 1_000_000 < 3_000) shouldBe true
        error.shouldBeInstanceOf<IOException>()
        (error is CloudflareBypassException) shouldBe false
        hosts.recentFailure(site.hostName, null) shouldBe null
        // The host's lock is free: the next request is solved at once.
        solver.enqueue(solved())
        client().newCall(GET(site.url("/popular").toString())).execute().use { it.code shouldBe 200 }

        // A session cancelled before it starts asks no solver.
        val before = solver.requestCount
        assertThrows<java.io.InterruptedIOException> { backend().run(session().copy(isCancelled = { true })) }
        solver.requestCount shouldBe before
    }

    @Test
    fun `answers with null or other types are read safely`() {
        val backend = backend()
        solver.enqueue(answer("""{"status": "ok", "solution": {"cookies": null, "userAgent": null, "url": 5, "status": "x"}}"""))
        backend.run(session()).let {
            it.outcome shouldBe BrowserSessionResult.Outcome.CLOSED
            it.cookies shouldBe emptyList()
            it.userAgent shouldBe null
            it.finalUrl shouldBe site.url("/")
        }
        solver.enqueue(
            answer(
                """{"status": "ok", "solution": {"userAgent": ["x"], "cookies": [null, 1, {"name": null, "value": "v"},
                   {"name": "cf_clearance", "value": "v", "secure": "yes", "expiry": {"x": 1}, "domain": null, "path": 3}]}}""",
            ),
        )
        backend.run(session()).let {
            it.outcome shouldBe BrowserSessionResult.Outcome.CONDITION_MET
            it.cookies.single().persistent shouldBe false
            it.userAgent shouldBe null
        }
        solver.enqueue(answer("""{"status": "ok", "solution": null}"""))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.NO_SOLUTION
        solver.enqueue(answer("""{"status": "ok", "solution": "x"}"""))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.NO_SOLUTION
        solver.enqueue(answer("""{"status": null, "message": null}"""))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.UNREADABLE_ANSWER
        solver.enqueue(answer("""{"status": ["error"], "message": {"text": "x"}}"""))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.UNREADABLE_ANSWER
        solver.enqueue(answer("""[1, 2]"""))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.UNREADABLE_ANSWER
        solver.enqueue(answer("null"))
        assertThrows<FlareSolverrException> { backend.run(session()) }.reason shouldBe FlareSolverrException.Reason.UNREADABLE_ANSWER

        solver.enqueue(answer("""{"msg": null, "version": {"major": 3}}"""))
        backend.testConnection() shouldBe FlareSolverrBackend.ConnectionTest.Ok(null)
        solver.enqueue(answer("null"))
        backend.testConnection().shouldBeInstanceOf<FlareSolverrBackend.ConnectionTest.Failed>()
        solver.enqueue(answer("""["msg"]"""))
        backend.testConnection().shouldBeInstanceOf<FlareSolverrBackend.ConnectionTest.Failed>()
    }

    @Test
    fun `the settings never render the address' password`() {
        FlareSolverrAddress.mask("http://user:secret@localhost:8191/") shouldBe "http://user:••••@localhost:8191/"
        FlareSolverrAddress.mask("https://user:p%40ss@solver.lan/flare/v1") shouldBe "https://user:••••@solver.lan/flare/v1"
        FlareSolverrAddress.mask("http://user@localhost:8191") shouldBe "http://user@localhost:8191"
        FlareSolverrAddress.mask("http://user:@localhost:8191") shouldBe "http://user:@localhost:8191"
        FlareSolverrAddress.mask("http://localhost:8191/a@b") shouldBe "http://localhost:8191/a@b"
        FlareSolverrAddress.mask("") shouldBe ""

        val stored = "http://user:secret@localhost:8191/"
        // Unchanged mask: the stored password stays (also with another host or path); a typed one replaces it.
        FlareSolverrAddress.unmask("http://user:••••@solver.lan:8191/v1", stored) shouldBe "http://user:secret@solver.lan:8191/v1"
        FlareSolverrAddress.unmask("http://user:new@localhost:8191/", stored) shouldBe "http://user:new@localhost:8191/"
        FlareSolverrAddress.unmask("http://localhost:8191/", stored) shouldBe "http://localhost:8191/"

        val store = DesktopPreferenceStore(dir.resolve("mask.json"))
        val raw = DesktopCloudflarePreferences(store).flareSolverrUrl()
        val shown = MaskedAddressPreference(raw)
        raw.set(stored)
        shown.get() shouldBe "http://user:••••@localhost:8191/"
        shown.set("http://user:••••@localhost:8192/")
        raw.get() shouldBe "http://user:secret@localhost:8192/"
        shown.set("")
        raw.get() shouldBe ""
    }

    private object ConnectTimeoutSocketFactory : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = throw SocketTimeoutException("connect timed out")
        }

        override fun createSocket(host: String?, port: Int): Socket = throw UnsupportedOperationException()

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            throw UnsupportedOperationException()

        override fun createSocket(host: InetAddress?, port: Int): Socket = throw UnsupportedOperationException()

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            throw UnsupportedOperationException()
    }

    private fun solvedBodyLater() = """{"status": "ok", "solution": {"cookies": [], "userAgent": "$SOLVER_UA"}}"""

    companion object {
        const val SOURCE_UA = "Mozilla/5.0 (source)"
        const val SOLVER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }
}
