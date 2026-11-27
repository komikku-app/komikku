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
    private val refreshLock = Any()

    @Volatile private var oauth = tracker.restoreToken()?.withAbsoluteExpiry()

    // Bumped on every auth change so a refresh that was started before a logout cannot restore the
    // session that the user just cleared.
    @Volatile private var sessionGeneration = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authorizedToken(chain) ?: throw IllegalStateException("Not authenticated with MangaBaka")
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${token.accessToken}").build())
    }

    private fun authorizedToken(chain: Interceptor.Chain): MangaBakaOAuth? {
        val current = oauth ?: return null
        if (!current.isExpired()) return current

        // MangaBaka rotates refresh tokens, so concurrent refreshes with the same token would fail.
        synchronized(refreshLock) {
            val token = oauth ?: return null
            // Another request may have refreshed while this one waited for the lock.
            if (!token.isExpired()) return token

            val generation = sessionGeneration
            val response = chain.proceed(MangaBakaApi.refreshTokenRequest(token.refreshToken))
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                // A rejected refresh token is permanent; transient failures must not sign the user out.
                if (code == 400 || code == 401 || code == 403) {
                    tracker.logout()
                    throw IllegalStateException("MangaBaka session expired; sign in again")
                }
                throw IllegalStateException("Could not refresh MangaBaka session (HTTP $code)")
            }

            val refreshed = with(json) { response.parseAs<MangaBakaOAuth>() }.withAbsoluteExpiry()
            response.close()
            if (generation != sessionGeneration) return null
            setAuth(refreshed)
            return refreshed
        }
    }

    fun setAuth(value: MangaBakaOAuth?) {
        sessionGeneration++
        oauth = value
        tracker.saveToken(value)
    }
}
// KMK <--
