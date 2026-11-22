package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MyAnimeListLoginTest {

    private val usernamePreference = InMemoryPreference("pref_mangasync_username_1", null, "")
    private val passwordPreference = InMemoryPreference("pref_mangasync_password_1", null, "")
    private val authExpiredPreference = InMemoryPreference("pref_tracker_auth_expired_1", null, false)
    private val tokenPreference = InMemoryPreference("track_token_1", null, "")
    private val codeVerifierPreference = InMemoryPreference("track_pkce_code_verifier_1", null, "")

    /** Requests that reached the network. */
    private val sent = CopyOnWriteArrayList<Request>()

    /** How the network answers each request, swapped by the tests. */
    @Volatile
    private var server: (Interceptor.Chain) -> Response = { malServer(it, tokenCode = 200, tokenBody = TOKEN_BODY) }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            sent += chain.request()
            server(chain)
        }
        .build()

    init {
        // Injekt keeps the instances registered first, so each test stubs the same mocks again.
        every { trackPreferences.trackUsername(any()) } returns usernamePreference
        every { trackPreferences.trackPassword(any()) } returns passwordPreference
        every { trackPreferences.trackAuthExpired(any()) } returns authExpiredPreference
        every { trackPreferences.trackToken(any()) } returns tokenPreference
        every { trackPreferences.trackPkceCodeVerifier(any()) } returns codeVerifierPreference
        every { trackPreferences.setCredentials(any(), any(), any()) } answers {
            usernamePreference.set(secondArg())
            passwordPreference.set(thirdArg())
            authExpiredPreference.set(false)
        }
        every { networkHelper.client } returns client
    }

    /** A new tracker, as in a new app process: nothing is kept in memory. */
    private fun tracker() = MyAnimeList(1L)

    @Test
    fun `login sends the code verifier stored before the process was killed and clears it`() {
        // Stored when the login URL was built, by a process killed while the browser was open. This process
        // never generated it.
        codeVerifierPreference.set("verifier-of-the-killed-process")
        val tracker = tracker()

        runBlocking { tracker.login("auth-code") }

        val form = sent.first().formFields()
        sent.first().url.encodedPath shouldBe "/v1/oauth2/token"
        form["code"] shouldBe "auth-code"
        form["code_verifier"] shouldBe "verifier-of-the-killed-process"
        codeVerifierPreference.isSet() shouldBe false
        tracker.isLoggedIn shouldBe true
        tracker.getUsername() shouldBe "mal-user"
        tracker.loadOAuth()?.accessToken shouldBe "login-access-token"
    }

    @Test
    fun `each login URL gets a new code verifier and the latest one is sent`() {
        val tracker = tracker()
        val first = MyAnimeListApi.newLoginCodeVerifier(trackPreferences, tracker)
        val second = MyAnimeListApi.newLoginCodeVerifier(trackPreferences, tracker)
        first shouldNotBe second
        codeVerifierPreference.get() shouldBe second

        runBlocking { tracker.login("auth-code") }

        sent.first().formFields()["code_verifier"] shouldBe second
        codeVerifierPreference.isSet() shouldBe false
        tracker.isLoggedIn shouldBe true
    }

    @Test
    fun `rejected code exchange clears the code verifier`() {
        codeVerifierPreference.set("stored-verifier")
        server = { malServer(it, tokenCode = 400, tokenBody = """{"error":"invalid_grant"}""") }
        val tracker = tracker()

        runBlocking { tracker.login("auth-code") }

        sent.single().formFields()["code_verifier"] shouldBe "stored-verifier"
        codeVerifierPreference.isSet() shouldBe false
        tracker.isLoggedIn shouldBe false
    }

    @Test
    fun `code exchange failing on the network clears the code verifier`() {
        codeVerifierPreference.set("stored-verifier")
        server = { throw IOException("Unable to resolve host") }
        val tracker = tracker()

        runBlocking { tracker.login("auth-code") }

        sent.size shouldBe 1
        codeVerifierPreference.isSet() shouldBe false
        tracker.isLoggedIn shouldBe false
    }

    @Test
    fun `cancelled code exchange keeps the code verifier and the session for the redelivered redirect`() {
        loggedInAlready()
        codeVerifierPreference.set("stored-verifier")
        val requestSent = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        server = {
            requestSent.countDown()
            releaseRequest.await(WAIT_SECONDS, TimeUnit.SECONDS)
            throw IOException("Canceled")
        }
        val tracker = tracker()

        try {
            runBlocking {
                val login = launch(Dispatchers.IO) { tracker.login("auth-code") }
                requestSent.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
                // The login activity is recreated, which cancels its scope.
                login.cancelAndJoin()
            }
        } finally {
            releaseRequest.countDown()
        }

        codeVerifierPreference.get() shouldBe "stored-verifier"
        tracker.getUsername() shouldBe "old-user"
        tracker.loadOAuth()?.accessToken shouldBe "old-access-token"

        // The recreated activity delivers the redirect again, and its exchange can still send the verifier.
        server = { malServer(it, tokenCode = 200, tokenBody = TOKEN_BODY) }
        runBlocking { tracker.login("auth-code") }
        sent.last { it.url.encodedPath == "/v1/oauth2/token" }.formFields()["code_verifier"] shouldBe "stored-verifier"
        codeVerifierPreference.isSet() shouldBe false
        tracker.getUsername() shouldBe "mal-user"
    }

    @Test
    fun `login without a stored code verifier fails without calling the server`() {
        // Logged in already, e.g. when the redirect is delivered a second time.
        loggedInAlready()
        val tracker = tracker()

        runBlocking { tracker.login("auth-code") }

        sent.size shouldBe 0
        // Nothing was exchanged, so the session the user had is kept.
        tracker.isLoggedIn shouldBe true
        tracker.getUsername() shouldBe "old-user"
        tracker.loadOAuth()?.accessToken shouldBe "old-access-token"
    }

    private fun loggedInAlready() {
        usernamePreference.set("old-user")
        passwordPreference.set("old-access-token")
        tokenPreference.set(
            """{"token_type":"Bearer","refresh_token":"old-refresh-token","access_token":"old-access-token",""" +
                """"expires_in":3600,"created_at":${System.currentTimeMillis() / 1000}}""",
        )
    }

    /** Answers like MAL: the token endpoint with [tokenCode] and [tokenBody], and the current user. */
    private fun malServer(chain: Interceptor.Chain, tokenCode: Int, tokenBody: String): Response {
        val request = chain.request()
        val (code, body) = when (request.url.encodedPath) {
            "/v1/oauth2/token" -> tokenCode to tokenBody
            "/v2/users/@me" -> 200 to """{"name":"mal-user"}"""
            else -> 404 to "{}"
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun Request.formFields(): Map<String, String> {
        val form = body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }

    companion object {
        private val trackPreferences = mockk<TrackPreferences>()
        private val networkHelper = mockk<NetworkHelper>()

        init {
            // MyAnimeList and its API and interceptor resolve these through Injekt.
            Injekt.addSingleton(trackPreferences)
            Injekt.addSingleton(networkHelper)
            Injekt.addSingleton(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}

/** Bound on every wait of the cancellation test, so that it fails instead of hanging. */
private const val WAIT_SECONDS = 10L

/** A successful code exchange response. */
private const val TOKEN_BODY = """{
    "token_type": "Bearer",
    "refresh_token": "login-refresh-token",
    "access_token": "login-access-token",
    "expires_in": 3600
}"""
