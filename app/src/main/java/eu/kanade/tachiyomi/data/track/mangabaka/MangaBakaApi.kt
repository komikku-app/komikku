package eu.kanade.tachiyomi.data.track.mangabaka

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaLibraryResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaSearchResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserProfile
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserProfileResponse
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

// KMK -->
class MangaBakaApi(private val trackerId: Long, baseClient: OkHttpClient, interceptor: MangaBakaInterceptor) {
    private val json: Json by injectLazy()
    private val client = baseClient.newBuilder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", "Komikku/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})").build())
    }.build()
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun add(track: Track): Track = withIOContext {
        val body = trackBody(track).toRequestBody(jsonMime)
        authClient.newCall(POST("$LIBRARY/${track.remote_id}", body = body)).awaitSuccess().close()
        track
    }
    suspend fun update(track: Track): Track = withIOContext {
        authClient.newCall(PUT("$LIBRARY/${track.remote_id}", body = trackBody(track).toRequestBody(jsonMime))).awaitSuccess().close()
        track
    }
    private fun trackBody(track: Track) = buildJsonObject {
        put("state", track.status.toApiState())
        put("is_private", track.private)
        put("progress_chapter", track.last_chapter_read.takeIf { it > 0 })
        put("rating", track.score.toInt().coerceIn(0, 100).takeIf { it > 0 })
        put("start_date", track.started_reading_date.takeIf { it > 0 }?.toLocalDateString())
        put("finish_date", track.finished_reading_date.takeIf { it > 0 }?.toLocalDateString())
    }.toString()
    suspend fun find(track: Track): Track? = withIOContext {
        try {
            val entry = with(json) { authClient.newCall(GET("$LIBRARY/${track.remote_id}")).awaitSuccess().parseAs<MangaBakaLibraryResult>().data }
            val item = with(json) { client.newCall(GET("$API/v1/series/${track.remote_id}")).awaitSuccess().parseAs<MangaBakaItemResult>().data }
            Track.create(trackerId).apply {
                remote_id = track.remote_id
                title = item.bestTitle()
                status = entry.state.toTrackStatus()
                score = entry.rating?.toDouble() ?: 0.0
                last_chapter_read = entry.progressChapter ?: 0.0
                private = entry.isPrivate
                started_reading_date = entry.startDate.toLocalEpoch()
                finished_reading_date = entry.finishDate.toLocalEpoch()
            }
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e
        }
    }
    suspend fun search(query: String): List<TrackSearch> = withIOContext {
        val uri = "$API/v1/series/search".toUri().buildUpon().appendQueryParameter("q", query).appendQueryParameter("type_not", "novel").build()
        with(json) { client.newCall(GET(uri.toString())).awaitSuccess().parseAs<MangaBakaSearchResult>().data.map(::toSearch) }
    }
    suspend fun searchById(id: Long): TrackSearch? = withIOContext {
        try {
            with(json) { client.newCall(GET("$API/v1/series/$id")).awaitSuccess().parseAs<MangaBakaItemResult>().data.let(::toSearch) }
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e
        }
    }
    private fun toSearch(item: MangaBakaItem) = TrackSearch.create(trackerId).apply {
        remote_id = item.id
        title = item.bestTitle()
        summary = item.description.orEmpty()
        score = item.rating ?: -1.0
        cover_url = item.cover.x250.x1.orEmpty()
        tracking_url = "$BASE/${item.id}"
        start_date = item.published.startDate.orEmpty()
        publishing_status = item.status
        publishing_type = item.type.replaceFirstChar(Char::uppercase)
        authors = item.authors.orEmpty()
        artists = item.artists.orEmpty()
    }
    suspend fun delete(id: Long) {
        authClient.newCall(DELETE("$LIBRARY/$id")).awaitSuccess().close()
    }
    suspend fun profile(): MangaBakaUserProfile = withIOContext { with(json) { authClient.newCall(GET("$API/v1/my/profile")).awaitSuccess().parseAs<MangaBakaUserProfileResponse>().data } }
    suspend fun exchange(code: String): MangaBakaOAuth = withIOContext {
        val form = FormBody.Builder().add("client_id", CLIENT_ID).add("code", code).add("code_verifier", verifier)
            .add("code_challenge_method", "S256").add("grant_type", "authorization_code").add("redirect_uri", REDIRECT).add("scope", SCOPES).build()
        with(json) { client.newCall(POST("$OAUTH/token", body = form)).awaitSuccess().parseAs<MangaBakaOAuth>() }
    }
    fun verifyState(state: String) = state == oauthState

    private fun Long.toLocalDateString() = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    private fun String?.toLocalEpoch(): Long = this?.substringBefore('T')?.let { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: 0L
    private fun String.toTrackStatus() = when (this) {
        "reading" -> 1L
        "completed" -> 2L
        "paused" -> 3L
        "dropped" -> 4L
        "plan_to_read" -> 5L
        "rereading" -> 6L
        "considering" -> 7L
        else -> 5L
    }
    private fun Long.toApiState() = when (this) {
        1L -> "reading"
        2L -> "completed"
        3L -> "paused"
        4L -> "dropped"
        5L -> "plan_to_read"
        6L -> "rereading"
        7L -> "considering"
        else -> "plan_to_read"
    }
    companion object {
        private const val BASE = "https://mangabaka.org"
        private const val API = "https://api.mangabaka.org"
        private const val LIBRARY = "$API/v1/my/library"
        private const val OAUTH = "$BASE/auth/oauth2"
        private const val CLIENT_ID = "zEZYMHXLWsLsafgbvJHXqzGvqQNOdkpo"
        private const val REDIRECT = "mihon://mangabaka-auth"
        private const val SCOPES = "library.read library.write offline_access openid"
        private var verifier = ""
        private var oauthState = ""
        fun authUrl(): Uri {
            val random = ByteArray(32).also(SecureRandom()::nextBytes)
            verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16).also(SecureRandom()::nextBytes))
            return "$OAUTH/authorize".toUri().buildUpon().appendQueryParameter("client_id", CLIENT_ID).appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256").appendQueryParameter("response_type", "code").appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("redirect_uri", REDIRECT).appendQueryParameter("state", oauthState).build()
        }
        fun refreshTokenRequest(token: String) = POST("$OAUTH/token", body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", CLIENT_ID).add("refresh_token", token).add("redirect_uri", REDIRECT).build())
        fun verifyOAuthState(state: String) = state == oauthState
    }
}
// KMK <--
