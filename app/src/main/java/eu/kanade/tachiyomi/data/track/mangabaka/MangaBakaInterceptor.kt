package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

// KMK -->
class MangaBakaInterceptor(private val tracker: MangaBaka) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile private var oauth = tracker.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        var token = oauth ?: throw IllegalStateException("Not authenticated with MangaBaka")
        if (token.isExpired()) {
            token = refresh(chain, token)
        }
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${token.accessToken}").build())
    }

    @Synchronized
    private fun refresh(chain: Interceptor.Chain, previous: MangaBakaOAuth): MangaBakaOAuth {
        val latest = oauth ?: throw IllegalStateException("Not authenticated with MangaBaka")
        if (latest != previous && !latest.isExpired()) return latest

        val response = chain.proceed(MangaBakaApi.refreshTokenRequest(latest.refreshToken))
        val body = response.use { it.body.string() }
        if (!response.isSuccessful) {
            val permanentlyInvalid = response.code == 401 || response.code == 403 ||
                (response.code == 400 && body.isInvalidGrant())
            if (permanentlyInvalid) tracker.logout()
            throw IllegalStateException(
                if (permanentlyInvalid) {
                    "MangaBaka session expired; sign in again"
                } else {
                    "Unable to refresh MangaBaka session (HTTP ${response.code})"
                },
            )
        }

        val refreshed = json.decodeFromString<MangaBakaOAuth>(body)
        setAuth(refreshed)
        return refreshed
    }

    private fun String.isInvalidGrant(): Boolean = runCatching {
        json.parseToJsonElement(this).jsonObject["error"]?.jsonPrimitive?.content == "invalid_grant"
    }.getOrDefault(false)

    fun setAuth(value: MangaBakaOAuth?) {
        oauth = value
        tracker.saveToken(value)
    }
}
// KMK <--
