package eu.kanade.tachiyomi.data.track.comick

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.comick.dto.ComickLibraryEntry
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.comick.dto.ComickSearchResult
import eu.kanade.tachiyomi.data.track.comick.dto.ComickTitleLookupResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import tachiyomi.domain.track.model.Track as DomainTrack

// KMK -->
class ComickApi(
    private val trackerId: Long,
    baseClient: OkHttpClient,
    interceptor: ComickInterceptor,
) {
    private val json: Json by injectLazy()
    private val client = baseClient.newBuilder().addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "Komikku/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
                .header("Accept", jsonMime.toString())
                .build(),
        )
    }.build()
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        val url = "$RESOURCE_URL/me/library".toUri().buildUpon().appendPath(track.remote_id.toHid()).build()
        val body = buildJsonObject {
            put("status", track.status.toApiListStatus())
            put("progress", progressJson(track.last_chapter_read))
            put("rating", track.score.toInt().takeIf { it > 0 })
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(PUT(url.toString(), body = body)).awaitSuccess().close()
        return track
    }

    suspend fun updateLibManga(track: Track): Track {
        val url = "$RESOURCE_URL/me/library".toUri().buildUpon().appendPath(track.remote_id.toHid()).build()
        val body = buildJsonObject {
            put("status", track.status.toApiListStatus())
            put("progress", progressJson(track.last_chapter_read))
            put("rating", track.score.toInt().takeIf { it > 0 })
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(PATCH(url.toString(), body = body)).awaitSuccess().close()
        return track
    }

    private fun progressJson(value: Double) = if (value <= 0.0) {
        JsonNull
    } else {
        buildJsonObject {
            put("number", value.toBigDecimal().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        val url = "$RESOURCE_URL/me/library".toUri().buildUpon().appendPath(track.remote_id.toHid()).build()
        return try {
            with(json) {
                authClient.newCall(GET(url.toString())).awaitSuccess().parseAs<ComickLibraryEntry>().toTrack(trackerId)
            }
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        val url = "$RESOURCE_URL/me/library".toUri().buildUpon().appendPath(track.remoteId.toHid()).build()
        authClient.newCall(DELETE(url.toString(), body = RequestBody.EMPTY)).awaitSuccess().close()
    }

    suspend fun search(query: String): List<TrackSearch> {
        val url = "$RESOURCE_URL/titles".toUri().buildUpon()
            .appendQueryParameter("q", query.take(100))
            .appendQueryParameter("media_type", "manga")
            .appendQueryParameter("limit", "20")
            .build()
        return with(json) {
            authClient.newCall(GET(url.toString())).awaitSuccess().parseAs<ComickSearchResult>().data
                .map { it.toTrackSearch(trackerId) }
        }
    }

    suspend fun getMangaDetails(hid: String): TrackSearch? {
        if (hid.length != 8) return null
        val url = "$RESOURCE_URL/titles".toUri().buildUpon().appendPath(hid).build()
        return try {
            with(json) {
                authClient.newCall(GET(url.toString())).awaitSuccess().parseAs<ComickTitleLookupResult>()
                    .data.toTrackSearch(trackerId)
            }
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e
        }
    }

    suspend fun getAccessToken(code: String): ComickOAuth {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", codeVerifier)
            .add("resource", RESOURCE_URL)
            .build()
        val body = client.newCall(POST("$OAUTH_URL/token", body = form)).awaitSuccess().use { it.body.string() }
        val oauth = json.decodeFromString<ComickOAuth>(body)
        if (oauth.refreshToken.isNullOrBlank() || "library:write" !in oauth.scope.split(' ')) {
            throw IllegalStateException("Comick access requires library write and offline access permissions")
        }
        return oauth
    }

    companion object {
        fun verifyOAuthState(state: String): Boolean = state == oauthState
        private const val CLIENT_ID = "SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH"
        private const val REDIRECT_URI = "app.mihon://comick-auth"
        private const val SCOPES = "library:read library:write offline_access"
        private const val OAUTH_URL = "https://comick.dev/api/auth/oauth2"
        private const val RESOURCE_URL = "https://api.comick.dev/integrations/v1"
        private var codeVerifier = ""
        private var oauthState = ""

        fun authUrl(): Uri {
            val random = ByteArray(16).also(SecureRandom()::nextBytes)
            oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
            val verifier = eu.kanade.tachiyomi.util.PkceUtil.generateCodeVerifier()
            codeVerifier = verifier
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            )
            return "$OAUTH_URL/authorize".toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("resource", RESOURCE_URL)
                .appendQueryParameter("state", oauthState)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()
        }

        fun refreshTokenRequest(refreshToken: String) = POST(
            "$OAUTH_URL/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("resource", RESOURCE_URL)
                .build(),
        )
    }
}
// KMK <--
