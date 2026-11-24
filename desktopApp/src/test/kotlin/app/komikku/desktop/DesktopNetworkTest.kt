package app.komikku.desktop

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.io.File
import java.io.IOException

/**
 * Smoke test of the shared network stack on desktop: `NetworkHelper.client` built through the compat
 * `Application`, the `GET` / `awaitSuccess` helpers, the cookie jar over the desktop `CookieManager`,
 * the default user agent and the desktop Cloudflare interceptor without a browser backend (P7: a challenge fails with
 * an actionable error; `DesktopCloudflareInterceptorTest` covers the rest). No real network.
 */
class DesktopNetworkTest {

    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()

    private val services by lazy {
        DesktopServices(
            application = Application(dir.resolve("data")),
            preferenceStore = DesktopPreferenceStore(dir.resolve("preferences.json")),
        )
    }

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    @Test
    fun `GET and awaitSuccess through NetworkHelper client`(): Unit = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Set-Cookie", "session=abc; Path=/")
                .body("hello desktop")
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(200).body("second").build())

        val client = services.network.client

        val first = client.newCall(GET(server.url("/manga/1").toString())).awaitSuccess()
        first.body.string() shouldBe "hello desktop"

        val second = client.newCall(
            GET(server.url("/manga/2").toString(), Headers.headersOf("Referer", "https://example.com/")),
        ).awaitSuccess()
        second.body.string() shouldBe "second"

        val firstRequest = server.takeRequest()
        firstRequest.headers["User-Agent"].orEmpty() shouldStartWith "Mozilla/5.0"
        val secondRequest = server.takeRequest()
        // Cookie set by the first response, stored by AndroidCookieJar in the desktop CookieManager.
        secondRequest.headers["Cookie"] shouldBe "session=abc"
        secondRequest.headers["Referer"] shouldBe "https://example.com/"

        // The HTTP cache lives in the compat context's cache dir.
        client.cache?.directory shouldBe dir.resolve("data/cache/network_cache")
    }

    @Test
    fun `awaitSuccess throws HttpException and a Cloudflare challenge needs a browser`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .addHeader("Server", "cloudflare")
                .body("<html><div id=\"challenge-error-title\"></div></html>")
                .build(),
        )

        val client = services.network.client

        val error = assertThrows<HttpException> {
            runBlocking { client.newCall(GET(server.url("/missing").toString())).awaitSuccess() }
        }
        error.code shouldBe 404

        // `await` wraps the interceptor's exception (coroutines may copy it too) and keeps its message, which the UI shows.
        val challenge = assertThrows<IOException> {
            runBlocking { client.newCall(GET(server.url("/protected").toString())).await() }
        }
        val cause = generateSequence<Throwable>(challenge) { it.cause }.filterIsInstance<CloudflareBypassException>().first()
        cause.reason shouldBe CloudflareBypassException.Reason.NO_BROWSER
        challenge.message shouldBe cause.message
    }
}
