package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

/**
 * The desktop copy `CloudflareChallenge`, pinned to `master`'s Android code (synced to 15610e67e4):
 * `CloudflareInterceptor.shouldIntercept` for what a challenge is, `WebViewUtil`'s `Headers.toWebViewHeaders` for the
 * headers a browser is given. Android and the desktop must decide both alike.
 */
class CloudflareChallengeTest {

    private fun response(code: Int, vararg headers: String, body: String = "") = Response.Builder()
        .request(Request.Builder().url("https://example.com/manga").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .headers(headersOf(*headers))
        .body(body.toResponseBody())
        .build()

    @Test
    fun `a challenge is a Cloudflare response marked cf-mitigated challenge, whatever its status`() {
        CloudflareChallenge.isChallenge(response(403, "Server", "cloudflare", "cf-mitigated", "challenge")) shouldBe true
        CloudflareChallenge.isChallenge(response(503, "Server", "cloudflare-nginx", "cf-mitigated", "challenge")) shouldBe true
        CloudflareChallenge.isChallenge(response(200, "Server", "cloudflare", "CF-Mitigated", "challenge")) shouldBe true
    }

    @Test
    fun `no cf-mitigated challenge, or not from Cloudflare, is no challenge`() {
        // The challenge page markers the old detection looked for, without the header
        val page = "<html><h1 id=\"challenge-error-title\">Just a moment...</h1></html>"
        CloudflareChallenge.isChallenge(response(403, "Server", "cloudflare", body = page)) shouldBe false
        CloudflareChallenge.isChallenge(response(403, "Server", "nginx", "cf-mitigated", "challenge")) shouldBe false
        CloudflareChallenge.isChallenge(response(403, "cf-mitigated", "challenge")) shouldBe false
        CloudflareChallenge.isChallenge(response(403, "Server", "cloudflare", "cf-mitigated", "block")) shouldBe false
    }

    @Test
    fun `browser headers drop the unsafe ones, Origin included`() {
        val headers = headersOf(
            "User-Agent", "Mozilla/5.0",
            "Origin", "https://example.com",
            "Host", "example.com",
            "Content-Length", "0",
            "Proxy-Authorization", "secret",
            "Connection", "Upgrade",
            "Accept", "text/html",
        )

        CloudflareChallenge.browserHeaders(headers) shouldBe mapOf("user-agent" to "Mozilla/5.0", "accept" to "text/html")
        CloudflareChallenge.browserHeaders(headersOf("Connection", "keep-alive")) shouldBe mapOf("connection" to "keep-alive")
    }

    @Test
    fun `browser headers merge the spellings of one name and keep its first value`() {
        val headers = headersOf("Referer", "https://example.com/a", "referer", "https://example.com/b")

        CloudflareChallenge.browserHeaders(headers) shouldBe mapOf("referer" to "https://example.com/a")
    }
}
