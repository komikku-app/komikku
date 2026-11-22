package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.Preference
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MangaDexLoginHelperTest {

    private val tokenPreference = InMemoryPreference("track_token_60", null, "")
    private val codeVerifierPreference = InMemoryPreference("track_pkce_code_verifier_60", null, "")
    private val trackPreferences = mockk<TrackPreferences> {
        every { trackToken(any()) } returns tokenPreference
        every { trackPkceCodeVerifier(any()) } returns codeVerifierPreference
    }
    private val notifier = mockk<MangaDexSessionNotifier>(relaxed = true)
    private val mdList = mockk<MdList> {
        every { sessionNotifier } returns notifier
    }
    private val interceptor = MangaDexAuthInterceptor(trackPreferences, mdList)

    /** Requests that reached the network, with whether the stored token was already gone when each was sent. */
    private val sent = CopyOnWriteArrayList<Pair<Request, Boolean>>()

    init {
        // Like the real MdList.logout(): clearing the interceptor also deletes the stored token.
        every { mdList.logout() } answers { interceptor.setAuth(null) }
    }

    @Test
    fun `login sends the code verifier stored before the process was killed and clears it`() {
        // Stored when the login URL was built, by a process killed while the browser was open. This process
        // never generated it.
        codeVerifierPreference.set("verifier-of-the-killed-process")

        val loggedIn = runBlocking { helper(tokenEndpoint(200, LOGIN_TOKEN_BODY)).login("auth-code") }

        loggedIn shouldBe true
        val form = sent.single().first.formFields()
        form["code"] shouldBe "auth-code"
        form["code_verifier"] shouldBe "verifier-of-the-killed-process"
        codeVerifierPreference.isSet() shouldBe false
        MdUtil.loadOAuth(trackPreferences, mdList)?.accessToken shouldBe "login-access-token"
        verify(exactly = 1) { notifier.dismiss() }
        verify(exactly = 0) { mdList.logout() }
    }

    @Test
    fun `each login URL gets a new code verifier and the latest one is sent`() {
        val first = MdUtil.newLoginCodeVerifier(trackPreferences, mdList)
        val second = MdUtil.newLoginCodeVerifier(trackPreferences, mdList)
        first shouldNotBe second
        codeVerifierPreference.get() shouldBe second

        runBlocking { helper(tokenEndpoint(200, LOGIN_TOKEN_BODY)).login("auth-code") } shouldBe true

        sent.single().first.formFields()["code_verifier"] shouldBe second
        codeVerifierPreference.isSet() shouldBe false
    }

    @Test
    fun `rejected code exchange clears the code verifier`() {
        codeVerifierPreference.set("stored-verifier")

        val loggedIn = runBlocking {
            helper(tokenEndpoint(400, """{"error":"invalid_grant","error_description":"PKCE verification failed"}"""))
                .login("auth-code")
        }

        loggedIn shouldBe false
        sent.single().first.formFields()["code_verifier"] shouldBe "stored-verifier"
        codeVerifierPreference.isSet() shouldBe false
        verify(exactly = 1) { mdList.logout() }
    }

    @Test
    fun `code exchange failing on the network clears the code verifier`() {
        codeVerifierPreference.set("stored-verifier")

        val loggedIn = runBlocking { helper(offlineClient()).login("auth-code") }

        loggedIn shouldBe false
        sent.size shouldBe 1
        codeVerifierPreference.isSet() shouldBe false
    }

    @Test
    fun `cancelled code exchange keeps the code verifier for the redelivered redirect`() {
        codeVerifierPreference.set("stored-verifier")
        val requestSent = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val hangingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                sent += chain.request() to tokenPreference.get().isEmpty()
                requestSent.countDown()
                releaseRequest.await(WAIT_SECONDS, TimeUnit.SECONDS)
                throw IOException("Canceled")
            }
            .build()

        try {
            runBlocking {
                val login = launch(Dispatchers.IO) { helper(hangingClient).login("auth-code") }
                requestSent.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
                // The login activity is recreated, which cancels its scope.
                login.cancelAndJoin()
            }
        } finally {
            releaseRequest.countDown()
        }

        codeVerifierPreference.get() shouldBe "stored-verifier"
        verify(exactly = 0) { mdList.logout() }

        // The recreated activity delivers the redirect again, and its exchange can still send the verifier.
        runBlocking { helper(tokenEndpoint(200, LOGIN_TOKEN_BODY)).login("auth-code") } shouldBe true
        sent.last().first.formFields()["code_verifier"] shouldBe "stored-verifier"
        codeVerifierPreference.isSet() shouldBe false
    }

    @Test
    fun `login without a stored code verifier fails without calling the server`() {
        // Logged in already, e.g. when the redirect is delivered a second time.
        val session = MALOAuth(
            tokenType = "Bearer",
            refreshToken = "refresh-token",
            accessToken = "access-token",
            expiresIn = 900,
            createdAt = System.currentTimeMillis() / 1000,
        )
        interceptor.setAuth(session)

        val loggedIn = runBlocking { helper(tokenEndpoint(200, LOGIN_TOKEN_BODY)).login("auth-code") }

        loggedIn shouldBe false
        sent.size shouldBe 0
        // Nothing was exchanged, so the session the user had is kept.
        verify(exactly = 0) { mdList.logout() }
        MdUtil.loadOAuth(trackPreferences, mdList) shouldBe session
    }

    @Test
    fun `code verifier is app state, so backups and sync never carry it`() {
        val tracker = mockk<MdList> { every { id } returns 60L }

        val key = TrackPreferences(InMemoryPreferenceStore()).trackPkceCodeVerifier(tracker).key()

        Preference.isAppState(key) shouldBe true
    }

    @Test
    fun `logout clears local state and returns true when the server call fails`() {
        interceptor.setAuth(
            MALOAuth(
                tokenType = "Bearer",
                refreshToken = "refresh-token",
                accessToken = "access-token",
                expiresIn = 900,
                createdAt = System.currentTimeMillis() / 1000,
            ),
        )

        val loggedOut = runBlocking { helper(offlineClient()).logout() }

        loggedOut shouldBe true
        verify(exactly = 1) { mdList.logout() }
        tokenPreference.get() shouldBe ""
        interceptor.token shouldBe null

        // The server logout was attempted after the local one, with the credentials read before clearing them.
        val (request, tokenAlreadyCleared) = sent.single()
        tokenAlreadyCleared shouldBe true
        request.url.toString() shouldBe MdApi.baseAuthUrl + MdApi.logout
        request.header("Authorization") shouldBe "Bearer access-token"
        val form = request.body as FormBody
        (0 until form.size).associate { form.name(it) to form.value(it) }["refresh_token"] shouldBe "refresh-token"
    }

    @Test
    fun `logout without stored credentials logs out locally and does not call the server`() {
        MdUtil.loadOAuth(trackPreferences, mdList) shouldBe null

        val loggedOut = runBlocking { helper(offlineClient()).logout() }

        loggedOut shouldBe true
        verify(exactly = 1) { mdList.logout() }
        sent.size shouldBe 0
    }

    private fun helper(client: OkHttpClient) =
        MangaDexLoginHelper(client, Headers.headersOf(), trackPreferences, mdList, interceptor)

    /** A client that records each request and then fails it as if the device were offline. */
    private fun offlineClient() = OkHttpClient.Builder()
        .addInterceptor { chain ->
            sent += chain.request() to tokenPreference.get().isEmpty()
            throw IOException("Unable to resolve host")
        }
        .build()

    /** A client that records each request and answers it with [code] and [body], as the token endpoint would. */
    private fun tokenEndpoint(code: Int, body: String) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            sent += chain.request() to tokenPreference.get().isEmpty()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP $code")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun Request.formFields(): Map<String, String> {
        val form = body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }
}

/** Bound on every wait of the cancellation test, so that it fails instead of hanging. */
private const val WAIT_SECONDS = 10L

/** A successful code exchange response. */
private const val LOGIN_TOKEN_BODY = """{
    "token_type": "Bearer",
    "refresh_token": "login-refresh-token",
    "access_token": "login-access-token",
    "expires_in": 900
}"""
