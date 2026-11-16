package app.komikku.desktop.track

import app.komikku.shared.track.OAuthRedirect
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 8.3: the "paste the redirected URL / code" login fallback for the app-scheme OAuth trackers. */
class OAuthRedirectTest {

    private val anilist = TrackerLoginMethod.OAuthPaste({ "" }, "anilist-auth", "access_token")
    private val myAnimeList = TrackerLoginMethod.OAuthPaste({ "" }, "myanimelist-auth", "code")

    @Test
    fun `token from an AniList implicit-grant fragment`() {
        val pasted = "komikku://anilist-auth#access_token=eyJ0eXAi.abc%2Bdef&token_type=Bearer&expires_in=31536000"
        OAuthRedirect.parse(pasted).host shouldBe "anilist-auth"
        DesktopTrackerLogin.credentialFromRedirect(anilist, pasted) shouldBe "eyJ0eXAi.abc+def"
    }

    @Test
    fun `code from a query redirect, with surrounding whitespace`() {
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "  komikku://myanimelist-auth?code=def50200a1&state=x \n") shouldBe "def50200a1"
    }

    @Test
    fun `bare code and bare parameter list`() {
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "def50200a1") shouldBe "def50200a1"
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "code=abc&state=1") shouldBe "abc"
        DesktopTrackerLogin.credentialFromRedirect(anilist, "access_token=tok") shouldBe "tok"
    }

    @Test
    fun `wrong tracker, missing value and empty input are rejected`() {
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "komikku://anilist-auth#access_token=x") shouldBe null
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "komikku://myanimelist-auth?error=access_denied") shouldBe null
        DesktopTrackerLogin.credentialFromRedirect(myAnimeList, "   ") shouldBe null
    }

    @Test
    fun `query wins over fragment like Android`() {
        OAuthRedirect.parse("komikku://x-auth?code=q#code=f").value("code") shouldBe "q"
    }
}
