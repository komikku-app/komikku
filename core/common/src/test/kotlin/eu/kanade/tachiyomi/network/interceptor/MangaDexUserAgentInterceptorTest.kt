package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.NetworkHelper.Companion.addUserAgentInterceptors
import io.kotest.matchers.shouldBe
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class MangaDexUserAgentInterceptorTest {

    private val appUserAgent = MangaDexUserAgentInterceptor.defaultUserAgent()
    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/141.0.0.0 Mobile"

    /**
     * Sends [request] through the User-Agent interceptors NetworkHelper's shared client uses, added by the same
     * function and so in the same order, and returns the User-Agent that left.
     */
    private fun sentUserAgent(request: Request): String? {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addUserAgentInterceptors { browserUserAgent }
            .addInterceptor(
                Interceptor { chain ->
                    sent = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                },
            )
            .build()
        client.newCall(request).execute().close()
        return sent?.header("User-Agent")
    }

    @Test
    fun `MangaDex hosts without a User-Agent get the app one instead of the browser one`() {
        listOf(
            "https://uploads.mangadex.org/covers/a/b.jpg",
            "https://api.mangadex.org/manga",
            "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/logout",
            "https://mangadex.org/title/a",
            "https://abc123.mangadex.network/data/hash/1.jpg",
            "https://api.mangadex.network/report",
        ).forEach { url ->
            sentUserAgent(Request.Builder().url(url).build()) shouldBe appUserAgent
        }
    }

    @Test
    fun `a User-Agent set by the caller is kept`() {
        val request = Request.Builder()
            .url("https://api.mangadex.org/manga")
            .header("User-Agent", "Custom/1.0")
            .build()
        sentUserAgent(request) shouldBe "Custom/1.0"
    }

    @Test
    fun `other hosts still get the default User-Agent`() {
        listOf(
            "https://api.similarmanga.com/similar/a.json",
            "https://notmangadex.org/a",
            "https://mangadex.org.example.com/a",
            "https://notmangadex.network/a",
        ).forEach { url ->
            sentUserAgent(Request.Builder().url(url).build()) shouldBe browserUserAgent
        }
    }
}
