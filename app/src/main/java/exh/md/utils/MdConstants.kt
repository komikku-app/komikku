@file:Suppress("PropertyName")

package exh.md.utils

import android.util.Base64
import androidx.core.net.toUri
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

object MdConstants {
    const val baseUrl = "https://mangadex.org"
    const val cdnUrl = "https://uploads.mangadex.org"
    const val atHomeReportUrl = "https://api.mangadex.network/report"

    object Types {
        const val author = "author"
        const val artist = "artist"
        const val coverArt = "cover_art"
        const val manga = "manga"
        const val scanlator = "scanlation_group"
    }

    val mdAtHomeTokenLifespan = 5.minutes.inWholeMilliseconds

    object Login {
        const val redirectUri = "tachiyomisy://mangadex-auth"
        const val clientId = "tachiyomisy"
        const val authorizationCode = "authorization_code"
        const val refreshToken = "refresh_token"

        // KMK -->
        // Request an offline refresh token so the app can keep renewing the session on its
        // own (offline tokens survive the browser SSO session) without forcing a re-login.
        const val scope = "openid offline_access"
        // KMK <--

        fun authUrl(codeVerifier: String): String {
            val bytes = codeVerifier.toByteArray()
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(bytes)
            val digest = messageDigest.digest()
            val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            val codeChallenge = Base64.encodeToString(digest, encoding)

            return (MdApi.baseAuthUrl + MdApi.login).toUri().buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                // KMK -->
                .appendQueryParameter("scope", scope)
                // KMK <--
                .build().toString()
        }
    }
}
