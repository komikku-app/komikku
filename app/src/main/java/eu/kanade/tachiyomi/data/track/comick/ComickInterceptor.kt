package eu.kanade.tachiyomi.data.track.comick

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

// KMK -->
class ComickInterceptor(private val comick: Comick) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile private var oauth: ComickOAuth? = comick.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        var token = oauth ?: throw IOException("Not authenticated with Comick")
        val refreshedBeforeRequest = token.isExpired()
        if (refreshedBeforeRequest) token = refresh(chain, token)

        val response = chain.proceed(chain.request().withAuth(token))
        if (response.code != 401 || refreshedBeforeRequest) return response

        response.close()
        val latestToken = oauth ?: throw IOException("Not authenticated with Comick")
        token = if (latestToken !== token && !latestToken.isExpired()) {
            latestToken
        } else {
            refresh(chain, token)
        }
        return chain.proceed(chain.request().withAuth(token))
    }

    @Synchronized
    private fun refresh(chain: Interceptor.Chain, previous: ComickOAuth): ComickOAuth {
        if (oauth !== previous && oauth?.isExpired() == false) return oauth!!
        val refreshToken = previous.refreshToken ?: run {
            comick.logout()
            throw IOException("Comick session expired; sign in again")
        }
        val response = chain.proceed(ComickApi.refreshTokenRequest(refreshToken))
        if (!response.isSuccessful) {
            val errorBody = response.use { it.body.string() }
            if (errorBody.isInvalidGrant()) {
                comick.logout()
                throw IOException("Comick session expired; sign in again")
            }
            throw IOException("Unable to refresh Comick session (HTTP ${response.code})")
        }
        val refreshed = response.use { json.decodeFromString<ComickOAuth>(it.body.string()) }
        if (refreshed.refreshToken.isNullOrBlank()) {
            comick.logout()
            throw IOException("Comick did not return a replacement refresh token; sign in again")
        }
        comick.saveToken(refreshed)
        oauth = refreshed
        return refreshed
    }

    private fun okhttp3.Request.withAuth(token: ComickOAuth) = newBuilder()
        .header("Authorization", "Bearer ${token.accessToken}")
        .header("User-Agent", "Komikku/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        .build()

    private fun String.isInvalidGrant(): Boolean = runCatching {
        json.parseToJsonElement(this).jsonObject["error"]?.jsonPrimitive?.content == "invalid_grant"
    }.getOrDefault(false)

    fun setAuth(value: ComickOAuth?) {
        oauth = value
        comick.saveToken(value)
    }
}
// KMK <--
