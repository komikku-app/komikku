package eu.kanade.tachiyomi.data.track.mdlist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import exh.md.network.MangaDexSessionNotifier
import exh.md.utils.MdUtil
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.registry.default.DefaultRegistrar

/**
 * Runs the real [MdList.logout], which [exh.md.network.MangaDexLoginHelperTest] stubs.
 *
 * Constructing [MdList] only resolves [TrackPreferences] from Injekt (its interceptor reads the stored token); the
 * other [eu.kanade.tachiyomi.data.track.BaseTracker] dependencies and the notifier's Application are resolved on
 * first use, which logout never reaches. So a fresh Injekt scope holding [TrackPreferences] is enough.
 */
class MdListTest {

    private val tokenPreference = InMemoryPreference("track_token", null, "")
    private val usernamePreference = InMemoryPreference("track_username", null, "")
    private val passwordPreference = InMemoryPreference("track_password", null, "")
    private val authExpiredPreference = InMemoryPreference("track_auth_expired", null, false)

    // InMemoryPreferenceStore returns a new copy of a preference on each lookup, so values set through it are
    // never read back: hand out the same preferences instead, while setCredentials still runs for real.
    private val trackPreferences = spyk(TrackPreferences(InMemoryPreferenceStore())) {
        every { trackToken(any()) } returns tokenPreference
        every { trackUsername(any()) } returns usernamePreference
        every { trackPassword(any()) } returns passwordPreference
        every { trackAuthExpired(any()) } returns authExpiredPreference
    }

    private lateinit var previousInjekt: InjektScope
    private lateinit var mdList: MdList

    @BeforeEach
    fun setUp() {
        previousInjekt = Injekt
        Injekt = InjektScope(DefaultRegistrar()).apply { addSingleton(trackPreferences) }
        // The real notifier needs an Application and the Android notification APIs; only its use is checked here.
        mockkConstructor(MangaDexSessionNotifier::class)
        every { anyConstructed<MangaDexSessionNotifier>().dismiss() } just runs

        mdList = MdList(TrackerManager.MDLIST)
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(MangaDexSessionNotifier::class)
        Injekt = previousInjekt
    }

    @Test
    fun `logout clears the interceptor credentials, deletes the stored token, resets the expired flag and dismisses the notification`() {
        mdList.saveCredentials("user", "password")
        mdList.interceptor.setAuth(
            MALOAuth(
                tokenType = "Bearer",
                refreshToken = "refresh-token",
                accessToken = "access-token",
                expiresIn = 900,
                createdAt = System.currentTimeMillis() / 1000,
            ),
        )
        // As if a refresh had been rejected earlier, so logout has to clear the flag.
        authExpiredPreference.set(true)
        mdList.isLoggedIn shouldBe true
        sendThroughInterceptor().header("Authorization") shouldBe "Bearer access-token"

        mdList.logout()

        // Stored credentials.
        tokenPreference.isSet() shouldBe false
        MdUtil.loadOAuth(trackPreferences, mdList) shouldBe null
        mdList.isLoggedIn shouldBe false
        mdList.getUsername() shouldBe ""
        mdList.getPassword() shouldBe ""
        authExpiredPreference.get() shouldBe false
        // In-memory credentials: requests are no longer authorized with the old token.
        mdList.interceptor.token shouldBe null
        sendThroughInterceptor().header("Authorization") shouldBe null

        verify(exactly = 1) { anyConstructed<MangaDexSessionNotifier>().dismiss() }
    }

    /** Sends a request through [MdList.interceptor] and returns it as it reached the network. */
    private fun sendThroughInterceptor(): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(mdList.interceptor)
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(MdUtil.baseUrl).build()).execute().close()
        return sent!!
    }
}
