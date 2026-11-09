package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
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
            val response = chain.proceed(MangaBakaApi.refreshTokenRequest(token.refreshToken))
            if (response.isSuccessful) {
                token = with(json) { response.parseAs<MangaBakaOAuth>() }
                response.close()
                setAuth(token)
            } else {
                response.close()
                throw IllegalStateException("MangaBaka session expired; sign in again")
            }
        }
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${token.accessToken}").build())
    }

    fun setAuth(value: MangaBakaOAuth?) {
        oauth = value
        tracker.saveToken(value)
    }
}
// KMK <--
