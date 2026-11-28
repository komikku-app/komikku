package app.komikku.desktop.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * [OAuthRedirectInbox]: a redirect the OS hands the app goes only to the login waiting for its scheme and host; without
 * one (or for another tracker, another scheme, after the login closed) it is ignored, so a web page cannot push a code.
 */
class OAuthRedirectInboxTest {

    private val waits = mutableListOf<AutoCloseable>()
    private val received = mutableListOf<String>()
    private var accepted = 0

    @AfterEach
    fun tearDown() {
        waits.forEach { it.close() }
        OAuthRedirectInbox.onAccepted = null
    }

    private fun await(scheme: String = "komikku", host: String = "myanimelist-auth", take: Boolean = true) =
        OAuthRedirectInbox.await(scheme, host) {
            received += it
            take
        }.also { waits += it }

    @Test
    fun `a redirect for the waiting login is handed to it and brings the window to the front`() {
        await()
        OAuthRedirectInbox.onAccepted = { accepted++ }

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=abc") shouldBe true

        received shouldBe listOf("komikku://myanimelist-auth?code=abc")
        accepted shouldBe 1
    }

    @Test
    fun `without a login in progress a redirect is ignored`() {
        OAuthRedirectInbox.onAccepted = { accepted++ }

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=attacker") shouldBe false

        accepted shouldBe 0
    }

    @Test
    fun `a redirect for another tracker, another scheme or an unknown host is ignored`() {
        await()

        OAuthRedirectInbox.deliver("komikku://anilist-auth#access_token=x") shouldBe false
        OAuthRedirectInbox.deliver("tachiyomisy://myanimelist-auth?code=x") shouldBe false
        OAuthRedirectInbox.deliver("komikku://evil.example/myanimelist-auth?code=x") shouldBe false
        OAuthRedirectInbox.deliver("komikku://?code=x") shouldBe false
        OAuthRedirectInbox.deliver("https://myanimelist-auth/?code=x") shouldBe false

        received shouldBe emptyList()
    }

    @Test
    fun `MDList waits on its own scheme`() {
        await(scheme = "tachiyomisy", host = "mangadex-auth")

        OAuthRedirectInbox.deliver("komikku://mangadex-auth?code=x") shouldBe false
        OAuthRedirectInbox.deliver("tachiyomisy://mangadex-auth?code=x") shouldBe true

        received shouldBe listOf("tachiyomisy://mangadex-auth?code=x")
    }

    @Test
    fun `scheme and host match without regard to case, surrounding whitespace is dropped`() {
        await()

        OAuthRedirectInbox.deliver("  KOMIKKU://MyAnimeList-Auth?code=abc\n") shouldBe true

        received shouldBe listOf("KOMIKKU://MyAnimeList-Auth?code=abc")
    }

    @Test
    fun `a closed login no longer takes redirects`() {
        await().close()

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=abc") shouldBe false

        received shouldBe emptyList()
    }

    @Test
    fun `a login that does not take the redirect leaves the window alone`() {
        await(take = false)
        OAuthRedirectInbox.onAccepted = { accepted++ }

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=abc") shouldBe false

        accepted shouldBe 0
    }

    @Test
    fun `a newer login for the same tracker replaces the older one`() {
        val older = mutableListOf<String>()
        OAuthRedirectInbox.await("komikku", "myanimelist-auth") {
            older += it
            true
        }.also { waits += it }
        await()

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=abc") shouldBe true

        older shouldBe emptyList()
        received shouldBe listOf("komikku://myanimelist-auth?code=abc")
    }

    @Test
    fun `a login that fails to take the redirect does not break the inbox`() {
        OAuthRedirectInbox.await("komikku", "myanimelist-auth") { error("boom") }.also { waits += it }

        OAuthRedirectInbox.deliver("komikku://myanimelist-auth?code=abc") shouldBe false
    }

    @Test
    fun `only printable ASCII tracker addresses of a bounded length are redirects`() {
        OAuthRedirectInbox.isRedirect("komikku://myanimelist-auth?code=abc") shouldBe true
        OAuthRedirectInbox.isRedirect("tachiyomisy://mangadex-auth?code=abc") shouldBe true
        OAuthRedirectInbox.isRedirect("komikku://myanimelist-auth?code=a b") shouldBe false
        OAuthRedirectInbox.isRedirect("komikku://myanimelist-auth?code=é") shouldBe false
        OAuthRedirectInbox.isRedirect("mihon://myanimelist-auth?code=abc") shouldBe false
        OAuthRedirectInbox.isRedirect("--self-test") shouldBe false
        OAuthRedirectInbox.isRedirect("komikku://a?code=" + "x".repeat(OAuthRedirectInbox.MAX_LENGTH)) shouldBe false
    }
}
