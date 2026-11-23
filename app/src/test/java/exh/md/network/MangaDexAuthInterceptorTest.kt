package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.interceptor.MangaDexUserAgentInterceptor
import exh.md.utils.MdUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class MangaDexAuthInterceptorTest {

    private val tokenPreference = InMemoryPreference("track_token_60", null, "")
    private val trackPreferences = mockk<TrackPreferences> {
        every { trackToken(any()) } returns tokenPreference
    }
    private val mdList = mockk<MdList>()

    /** The interceptors' clock. Starts close to overflow, which System.nanoTime() allows, to check it is handled. */
    private var fakeNanoTime = Long.MAX_VALUE - 5.seconds.inWholeNanoseconds

    /** Calls to the token endpoint, across all chains made by [chain]. */
    private var tokenRequests = 0

    /** The last request sent to the token endpoint by a chain made by [chain]. */
    private var lastTokenRequest: Request? = null

    @Test
    fun `refresh request sends the non-browser MangaDex User-Agent`() {
        val interceptor = loggedInInterceptor()

        interceptor.intercept(chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY))

        tokenRequests shouldBe 1
        val userAgent = lastTokenRequest?.header("User-Agent")
        userAgent shouldBe MangaDexUserAgentInterceptor.defaultUserAgent()
        userAgent!!.startsWith("Tachiyomi ") shouldBe true
    }

    @Test
    fun `refresh request is a public client refresh grant with no other fields or credentials`() {
        val interceptor = loggedInInterceptor(
            oauth = MALOAuth(
                tokenType = "Bearer",
                refreshToken = "stored-refresh-token-a1b2c3",
                accessToken = "expired-access-token",
                expiresIn = 900,
                createdAt = 0,
            ),
        )

        interceptor.intercept(chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY))

        tokenRequests shouldBe 1
        val sent = lastTokenRequest!!
        sent.method shouldBe "POST"
        sent.url.toString() shouldBe "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"
        // In order, each once: no code_verifier or redirect_uri, which Keycloak's refresh grant does not read.
        sent.formFields() shouldBe listOf(
            "client_id" to "tachiyomisy",
            "grant_type" to "refresh_token",
            "refresh_token" to "stored-refresh-token-a1b2c3",
        )
        // No Authorization header with the expired access token: only the User-Agent, and the POST() default
        // Cache-Control.
        sent.headers.toMultimap() shouldBe mapOf(
            "user-agent" to listOf(MangaDexUserAgentInterceptor.defaultUserAgent()),
            "cache-control" to listOf("max-age=600"),
        )
        sent.body?.contentType() shouldBe "application/x-www-form-urlencoded".toMediaType()
    }

    @Test
    fun `invalid_grant on 400 or 401 is a rejection`() {
        isRefreshRejected(400, """{"error":"invalid_grant","error_description":"Token is not active"}""") shouldBe true
        isRefreshRejected(401, """{"error":"invalid_grant"}""") shouldBe true
    }

    @Test
    fun `other OAuth errors on 400 or 401 are not a rejection`() {
        isRefreshRejected(401, """{"error":"invalid_client"}""") shouldBe false
        isRefreshRejected(400, """{"error":"invalid_client"}""") shouldBe false
        isRefreshRejected(400, """{"error":"unauthorized_client"}""") shouldBe false
        isRefreshRejected(400, """{"error":"not_allowed"}""") shouldBe false
        isRefreshRejected(400, """{"error":"invalid_scope"}""") shouldBe false
        isRefreshRejected(400, """{"error":"invalid_request"}""") shouldBe false
        // Matched exactly, case included.
        isRefreshRejected(400, """{"error":"INVALID_GRANT"}""") shouldBe false
        isRefreshRejected(401, """{"error":"Invalid_Grant"}""") shouldBe false
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
    fun `OAuth error other than invalid_grant keeps the stored token and does not report the session`() {
        var rejections = 0
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections++ })
        val storedToken = tokenPreference.get()

        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 400, tokenBody = """{"error":"invalid_client"}"""))
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

    @Test
    fun `refresh fails fast within the cooldown after a transient failure`() {
        val interceptor = loggedInInterceptor()
        val chain = chain(tokenStatus = 503, tokenBody = "Service Unavailable")
        shouldThrow<IOException> { interceptor.intercept(chain) }
        tokenRequests shouldBe 1

        // A request queued behind the failure, then one just before the cooldown ends.
        val queued = shouldThrow<IOException> { interceptor.intercept(chain) }
        fakeNanoTime += 9.seconds.inWholeNanoseconds
        shouldThrow<IOException> { interceptor.intercept(chain) }

        tokenRequests shouldBe 1
        queued.cause?.message shouldBe "Unexpected refresh response: HTTP 503"
    }

    @Test
    fun `refresh is attempted again once the cooldown has passed`() {
        val interceptor = loggedInInterceptor()
        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 503, tokenBody = "Service Unavailable"))
        }

        fakeNanoTime += 10.seconds.inWholeNanoseconds
        val response = interceptor.intercept(chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY))

        tokenRequests shouldBe 2
        response.request.header("Authorization") shouldBe "Bearer refreshed-access-token"
    }

    @Test
    fun `new login clears the cooldown`() {
        val interceptor = loggedInInterceptor()
        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 503, tokenBody = "Service Unavailable"))
        }

        interceptor.setAuth(loginOAuth(accessToken = "login-access-token"))

        // Still within the cooldown, but the new token is refreshed as soon as the API reports it expired.
        val response = interceptor.intercept(
            chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY, expiredAccessToken = "login-access-token"),
        )
        tokenRequests shouldBe 2
        response.code shouldBe 200
        response.request.header("Authorization") shouldBe "Bearer refreshed-access-token"
    }

    @Test
    fun `401 retry returns the original response on a transient failure and within the cooldown`() {
        val interceptor = loggedInInterceptor(oauth = loginOAuth(accessToken = "access-token"))
        val storedToken = tokenPreference.get()
        val chain = chain(tokenStatus = 503, tokenBody = "Service Unavailable", expiredAccessToken = "access-token")

        interceptor.intercept(chain).code shouldBe 401
        interceptor.intercept(chain).code shouldBe 401

        tokenRequests shouldBe 1
        tokenPreference.get() shouldBe storedToken
    }

    @Test
    fun `rejection after the cooldown clears the stored token and reports the session once`() {
        var rejections = 0
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections++ })
        shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 503, tokenBody = "Service Unavailable"))
        }

        fakeNanoTime += 10.seconds.inWholeNanoseconds
        val rejecting = chain(tokenStatus = 400, tokenBody = """{"error":"invalid_grant"}""")
        shouldThrow<IOException> { interceptor.intercept(rejecting) }

        tokenRequests shouldBe 2
        tokenPreference.get() shouldBe ""
        interceptor.token shouldBe null
        rejections shouldBe 1

        interceptor.intercept(rejecting).request.header("Authorization") shouldBe null
        rejections shouldBe 1
    }

    @Test
    fun `logout does not wait for a refresh in flight, whose result is then discarded`() {
        val rejections = AtomicInteger()
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections.incrementAndGet() })
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val chain = chain(
            tokenStatus = 200,
            tokenBody = REFRESHED_TOKEN_BODY,
            beforeTokenResponse = {
                refreshStarted.countDown()
                releaseRefresh.await(WAIT_SECONDS, TimeUnit.SECONDS)
            },
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = executor.submit<Response> { interceptor.intercept(chain) }
            refreshStarted.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true

            // The refresh is blocked on the network, the logout must not wait for it.
            measureTime { interceptor.setAuth(null) } shouldBeLessThan 2.seconds
            request.isDone shouldBe false

            releaseRefresh.countDown()
            shouldThrow<ExecutionException> { request.get(WAIT_SECONDS, TimeUnit.SECONDS) }
                .cause.shouldBeInstanceOf<IOException>()
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }

        // The token refreshed after the logout was not saved.
        tokenRequests shouldBe 1
        tokenPreference.get() shouldBe ""
        interceptor.token shouldBe null
        rejections.get() shouldBe 0
    }

    @Test
    fun `new login during a refresh in flight survives that refresh being rejected`() {
        val rejections = AtomicInteger()
        val interceptor = loggedInInterceptor(onSessionRejected = { rejections.incrementAndGet() })
        val login = loginOAuth(accessToken = "login-access-token")
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val chain = chain(
            tokenStatus = 400,
            tokenBody = """{"error":"invalid_grant"}""",
            beforeTokenResponse = {
                refreshStarted.countDown()
                releaseRefresh.await(WAIT_SECONDS, TimeUnit.SECONDS)
            },
        )
        val executor = Executors.newSingleThreadExecutor()
        val response = try {
            val request = executor.submit<Response> { interceptor.intercept(chain) }
            refreshStarted.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true

            // The refresh is blocked on the network, the login must not wait for it.
            measureTime { interceptor.setAuth(login) } shouldBeLessThan 2.seconds
            request.isDone shouldBe false

            releaseRefresh.countDown()
            request.get(WAIT_SECONDS, TimeUnit.SECONDS)
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }

        // The old session's rejection did not clear the new login, which the request went out with.
        response.request.header("Authorization") shouldBe "Bearer login-access-token"
        tokenRequests shouldBe 1
        MdUtil.loadOAuth(trackPreferences, mdList) shouldBe login
        interceptor.token shouldBe "login-access-token"
        rejections.get() shouldBe 0
    }

    @Test
    fun `refresh failing because its call was canceled does not start the cooldown`() {
        val interceptor = loggedInInterceptor()
        val storedToken = tokenPreference.get()
        val canceledChain = chain(
            tokenStatus = 200,
            tokenBody = REFRESHED_TOKEN_BODY,
            canceled = true,
            beforeTokenResponse = { throw IOException("Canceled") },
        )
        shouldThrow<IOException> { interceptor.intercept(canceledChain) }.cause?.message shouldBe "Canceled"
        tokenPreference.get() shouldBe storedToken

        // Well within the cooldown a failure would have started, the next request refreshes the token for real.
        val response = interceptor.intercept(chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY))
        tokenRequests shouldBe 2
        response.request.header("Authorization") shouldBe "Bearer refreshed-access-token"
    }

    @Test
    fun `network error on a call that was not canceled starts the cooldown`() {
        val interceptor = loggedInInterceptor()
        shouldThrow<IOException> {
            interceptor.intercept(
                chain(
                    tokenStatus = 200,
                    tokenBody = REFRESHED_TOKEN_BODY,
                    beforeTokenResponse = { throw SocketTimeoutException("timeout") },
                ),
            )
        }

        // The token endpoint would answer now, but the next request fails fast with the same error.
        val error = shouldThrow<IOException> {
            interceptor.intercept(chain(tokenStatus = 200, tokenBody = REFRESHED_TOKEN_BODY))
        }
        tokenRequests shouldBe 1
        error.cause.shouldBeInstanceOf<SocketTimeoutException>()
    }

    /** An interceptor logged in with [oauth], by default an expired access token so its first request refreshes it. */
    private fun loggedInInterceptor(
        onSessionRejected: () -> Unit = {},
        oauth: MALOAuth = MALOAuth(
            tokenType = "Bearer",
            refreshToken = "refresh-token",
            accessToken = "access-token",
            expiresIn = 900,
            createdAt = 0,
        ),
    ): MangaDexAuthInterceptor {
        MdUtil.saveOAuth(trackPreferences, mdList, oauth)
        return MangaDexAuthInterceptor(trackPreferences, mdList, onSessionRejected, nanoTime = { fakeNanoTime })
    }

    /** Credentials as a login returns them, valid for 15 minutes. */
    private fun loginOAuth(accessToken: String) = MALOAuth(
        tokenType = "Bearer",
        refreshToken = "refresh-token",
        accessToken = accessToken,
        expiresIn = 900,
        createdAt = System.currentTimeMillis() / 1000,
    )

    /**
     * A chain answering the token endpoint with [tokenStatus] and [tokenBody], requests sent with
     * [expiredAccessToken] with MangaDex's 401 for an expired access token, and any other request with 200.
     * [beforeTokenResponse] runs before each token endpoint answer, to block or fail it. Its call reports
     * [canceled].
     */
    private fun chain(
        tokenStatus: Int,
        tokenBody: String,
        expiredAccessToken: String? = null,
        canceled: Boolean = false,
        beforeTokenResponse: () -> Unit = {},
    ): Interceptor.Chain {
        val request = Request.Builder().url("https://api.mangadex.org/user/follows/manga").build()
        val chainCall = mockk<Call> { every { isCanceled() } returns canceled }
        return mockk {
            every { request() } returns request
            every { call() } returns chainCall
            every { proceed(any()) } answers {
                val sent = firstArg<Request>()
                when {
                    sent.url.host == "auth.mangadex.org" -> {
                        tokenRequests++
                        lastTokenRequest = sent
                        beforeTokenResponse()
                        response(sent, tokenStatus, tokenBody)
                    }
                    expiredAccessToken != null && sent.header("Authorization") == "Bearer $expiredAccessToken" ->
                        response(sent, 401, "{}").newBuilder()
                            .header("www-authenticate", EXPIRED_TOKEN_CHALLENGE)
                            .build()
                    else -> response(sent, 200, "{}")
                }
            }
        }
    }

    private fun Request.formFields(): List<Pair<String, String>> {
        val form = body as FormBody
        return (0 until form.size).map { form.name(it) to form.value(it) }
    }

    private fun response(request: Request, code: Int, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("HTTP $code")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}

/** A successful token endpoint response. */
private const val REFRESHED_TOKEN_BODY = """{
    "token_type": "Bearer",
    "refresh_token": "refreshed-refresh-token",
    "access_token": "refreshed-access-token",
    "expires_in": 900
}"""

/** Bound on every wait of the concurrency tests, so that they fail instead of hanging. */
private const val WAIT_SECONDS = 10L

/** The `www-authenticate` header MangaDex sends along a 401 for an expired access token. */
private const val EXPIRED_TOKEN_CHALLENGE =
    "Bearer error=\"invalid_token\", error_description=\"The access token expired\""
