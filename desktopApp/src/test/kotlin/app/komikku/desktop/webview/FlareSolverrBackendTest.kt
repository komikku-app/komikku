package app.komikku.desktop.webview

import android.app.Application
import android.webkit.CookieManager
import app.komikku.desktop.DesktopServices
import app.komikku.desktop.ui.more.flareSolverrTestMessage
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserUnavailableException
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.NoBrowserBackend
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.kotest.matchers.shouldBe
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
import java.util.concurrent.TimeUnit
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

    private fun backend(minTimeout: kotlin.time.Duration = 1.seconds, answerMargin: kotlin.time.Duration = 5.seconds) =
        FlareSolverrBackend({ endpoint }, minTimeout = minTimeout, answerMargin = answerMargin)

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
                    MockResponse.Builder().code(403).addHeader("Server", "cloudflare")
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
        services.network.client.newCall(GET(site.url("/popular").toString())).execute().use { it.code shouldBe 200 }

        // It shows no pages: "Open in WebView" opens the system browser and the cookie dialog.
        val webView = DesktopWebView(services.network, services.networkPreferences, io.mockk.mockk(relaxed = true), browserOpener = { true })
        webView.open(site.url("/").toString(), null).shouldBeInstanceOf<DesktopWebView.Opened.Manual>()
        solver.requestCount shouldBe 1
    }

    @Test
    fun `addresses are normalised to the API and only http(s) is accepted`() {
        FlareSolverrBackend.apiUrl("http://localhost:8191").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl("http://localhost:8191/").toString() shouldBe "http://localhost:8191/v1"
        FlareSolverrBackend.apiUrl(" https://solver.lan/flare/v1?x=1 ").toString() shouldBe "https://solver.lan/flare/v1"
        FlareSolverrBackend.apiUrl("https://solver.lan/flare/").toString() shouldBe "https://solver.lan/flare/v1"
        FlareSolverrBackend.baseUrl("http://localhost:8191/v1").toString() shouldBe "http://localhost:8191/"
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

    private fun solvedBodyLater() = """{"status": "ok", "solution": {"cookies": [], "userAgent": "$SOLVER_UA"}}"""

    companion object {
        const val SOURCE_UA = "Mozilla/5.0 (source)"
        const val SOLVER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }
}
