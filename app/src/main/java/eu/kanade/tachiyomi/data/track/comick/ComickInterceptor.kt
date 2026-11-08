package eu.kanade.tachiyomi.data.track.comick

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

// KMK -->
class ComickInterceptor(private val comick: Comick) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile private var oauth: ComickOAuth? = comick.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val current = oauth ?: throw IOException("Not authenticated with Comick")
        if (current.isExpired()) refresh(chain, current)
        val token = oauth ?: throw IOException("Not authenticated with Comick")
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${token.accessToken}")
                .header("User-Agent", "Komikku/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
                .build(),
        )
    }

    @Synchronized
    private fun refresh(chain: Interceptor.Chain, previous: ComickOAuth) {
        if (oauth !== previous && oauth?.isExpired() == false) return
        val refreshToken = previous.refreshToken ?: run {
            comick.logout()
            throw IOException("Comick session expired; sign in again")
        }
        val response = chain.proceed(ComickApi.refreshTokenRequest(refreshToken))
        if (!response.isSuccessful) {
            response.close()
            comick.logout()
            throw IOException("Unable to refresh Comick session")
        }
        val refreshed = response.use { json.decodeFromString<ComickOAuth>(it.body.string()) }
        comick.saveToken(refreshed)
        oauth = refreshed
    }

    fun setAuth(value: ComickOAuth?) {
        oauth = value
        comick.saveToken(value)
    }
}
// KMK <--
