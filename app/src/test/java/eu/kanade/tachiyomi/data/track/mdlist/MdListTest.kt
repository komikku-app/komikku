package eu.kanade.tachiyomi.data.track.mdlist

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.network.MangaDexSessionNotifier
import exh.md.utils.MdUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
import tachiyomi.domain.source.service.SourceManager
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

    // KMK -->
    @Test
    fun `a missing MangaDex source is looked up again on the next use, a found one is kept`() = runTest {
        // Resolved by the default arguments of MdUtil.getEnabledMangaDex(), whose result is stubbed below
        Injekt.addSingleton(mockk<SourcePreferences>())
        Injekt.addSingleton(mockk<SourceManager>())
        val mangaDex = mockk<MangaDex>()
        coEvery { mangaDex.fetchTrackingInfo(any()) } returns Track.create(TrackerManager.MDLIST)
        val track = Track.create(TrackerManager.MDLIST).apply { tracking_url = "${MdUtil.baseUrl}/title/id" }
        mockkObject(MdUtil)
        try {
            // Not installed, not enabled or not loaded yet at the first use; there at the second
            every { MdUtil.getEnabledMangaDex(any(), any()) } returnsMany listOf(null, mangaDex)

            shouldThrow<MdList.MangaDexNotFoundException> { mdList.refresh(track) }
            mdList.refresh(track)
            mdList.refresh(track)

            verify(exactly = 2) { MdUtil.getEnabledMangaDex(any(), any()) }
            coVerify(exactly = 2) { mangaDex.fetchTrackingInfo(track.tracking_url) }
        } finally {
            unmockkObject(MdUtil)
        }
    }
    // KMK <--

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
