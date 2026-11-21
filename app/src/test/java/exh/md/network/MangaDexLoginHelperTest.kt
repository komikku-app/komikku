package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class MangaDexLoginHelperTest {

    private val tokenPreference = InMemoryPreference("track_token_60", null, "")
    private val trackPreferences = mockk<TrackPreferences> {
        every { trackToken(any()) } returns tokenPreference
    }
    private val mdList = mockk<MdList>()
    private val interceptor = MangaDexAuthInterceptor(trackPreferences, mdList)

    /** Requests that reached the network, with whether the stored token was already gone when each was sent. */
    private val sent = CopyOnWriteArrayList<Pair<Request, Boolean>>()

    init {
        // Like the real MdList.logout(): clearing the interceptor also deletes the stored token.
        every { mdList.logout() } answers { interceptor.setAuth(null) }
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
}
