package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import exh.md.utils.MdUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import java.io.IOException

class MangaDexAuthInterceptorTest {

    private val tokenPreference = InMemoryPreference("track_token_60", null, "")
    private val trackPreferences = mockk<TrackPreferences> {
        every { trackToken(any()) } returns tokenPreference
    }
    private val mdList = mockk<MdList>()

    @Test
    fun `OAuth error body on 400 or 401 is a rejection`() {
        isRefreshRejected(400, """{"error":"invalid_grant","error_description":"Token is not active"}""") shouldBe true
        isRefreshRejected(401, """{"error":"invalid_client"}""") shouldBe true
        isRefreshRejected(400, """{"error":"not_allowed"}""") shouldBe true
    }

    @Test
    fun `non OAuth body on 400 is not a rejection`() {
        isRefreshRejected(400, "<html><body>Just a moment...</body></html>") shouldBe false
        isRefreshRejected(400, "") shouldBe false
    }

    @Test
    fun `JSON without a usable error field on 400 is not a rejection`() {
        isRefreshRejected(400, """{"error_description":"Token is not active"}""") shouldBe false
        isRefreshRejected(400, """{"error":""}""") shouldBe false
        isRefreshRejected(400, """{"error":"   "}""") shouldBe false
        isRefreshRejected(400, """{"error":400}""") shouldBe false
        isRefreshRejected(400, """{"error":null}""") shouldBe false
        isRefreshRejected(400, """{"error":{"code":"invalid_grant"}}""") shouldBe false
        isRefreshRejected(400, """["invalid_grant"]""") shouldBe false
    }

    @Test
    fun `OAuth error body on other status codes is not a rejection`() {
        val body = """{"error":"invalid_grant","error_description":"Token is not active"}"""
        isRefreshRejected(403, body) shouldBe false
        isRefreshRejected(429, body) shouldBe false
        isRefreshRejected(500, body) shouldBe false
        isRefreshRejected(503, body) shouldBe false
    }

    @Test
    fun `rejected refresh clears the stored token and reports the session once`() {
        var rejections = 0
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections++ })
        val chain = chain(tokenStatus = 400, tokenBody = """{"error":"invalid_grant"}""")

        shouldThrow<IOException> { interceptor.intercept(chain) }
        tokenPreference.get() shouldBe ""
        interceptor.token shouldBe null
        rejections shouldBe 1

        // Later requests go out without credentials and do not report the rejection again.
        interceptor.intercept(chain).request.header("Authorization") shouldBe null
        rejections shouldBe 1
    }

    @Test
    fun `transient refresh failure keeps the stored token and does not report the session`() {
        var rejections = 0
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections++ })
        val storedToken = tokenPreference.get()

        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 503, tokenBody = "Service Unavailable"))
        }
        tokenPreference.get() shouldBe storedToken
        rejections shouldBe 0
    }

    @Test
    fun `failing session callback does not escape the interceptor`() {
        val interceptor = loggedInInterceptor(onSessionRejected = { throw IllegalStateException("boom") })

        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 400, tokenBody = """{"error":"invalid_grant"}"""))
        }
        tokenPreference.get() shouldBe ""
    }

    /** An interceptor logged in with an expired access token, so its first request refreshes it. */
    private fun loggedInInterceptor(onSessionRejected: () -> Unit): MangaDexAuthInterceptor {
        val expired = MALOAuth(
            tokenType = "Bearer",
            refreshToken = "refresh-token",
            accessToken = "access-token",
            expiresIn = 900,
            createdAt = 0,
        )
        MdUtil.saveOAuth(trackPreferences, mdList, expired)
        return MangaDexAuthInterceptor(trackPreferences, mdList, onSessionRejected)
    }

    /** A chain answering the token endpoint with [tokenStatus] and [tokenBody], and any other request with 200. */
    private fun chain(tokenStatus: Int, tokenBody: String): Interceptor.Chain {
        val request = Request.Builder().url("https://api.mangadex.org/user/follows/manga").build()
        return mockk {
            every { request() } returns request
            every { proceed(any()) } answers {
                val sent = firstArg<Request>()
                if (sent.url.host == "auth.mangadex.org") {
                    response(sent, tokenStatus, tokenBody)
                } else {
                    response(sent, 200, "{}")
                }
            }
        }
    }

    private fun response(request: Request, code: Int, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("HTTP $code")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
