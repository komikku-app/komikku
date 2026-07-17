package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.parseAs
import exh.md.utils.MdUtil
import exh.util.nullIfBlank
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.io.IOException

class MangaDexAuthInterceptor(
    private val trackPreferences: TrackPreferences,
    private val mdList: MdList,
) : Interceptor {

    var token = trackPreferences.trackToken(mdList).get().nullIfBlank()

    private var oauth: MALOAuth? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }
        if (oauth == null) {
            oauth = MdUtil.loadOAuth(trackPreferences, mdList)
        }

        if (oauth == null) {
            throw IOException("No authentication token")
        }

        // KMK -->
        // Refresh access token if expired. Only clear the stored credentials when the
        // refresh token is genuinely rejected by the server; transient failures (no
        // network, server errors, ...) must keep the credentials so the app can keep
        // renewing on its own instead of forcing a browser re-login.
        if (oauth!!.isExpired()) {
            when (val result = refreshToken(chain)) {
                is RefreshResult.Success -> setAuth(result.oauth)
                is RefreshResult.InvalidGrant -> {
                    setAuth(null)
                    throw IOException("MangaDex session expired, please log in again")
                }
                is RefreshResult.TransientError -> {
                    throw IOException("Could not refresh MangaDex token", result.cause)
                }
            }
        }
        // KMK <--

        // Add the authorization header to the original request
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
            .build()

        val response = chain.proceed(authRequest)
        val tokenIsExpired = response.headers["www-authenticate"]
            ?.contains("The access token expired") ?: false

        // Retry the request once with a new token in case it was not already refreshed
        // by the is expired check before.
        if (response.code == 401 && tokenIsExpired) {
            // KMK -->
            val newToken = when (val result = refreshToken(chain)) {
                is RefreshResult.Success -> result.oauth.also { setAuth(it) }
                is RefreshResult.InvalidGrant -> {
                    setAuth(null)
                    return response
                }
                // Keep credentials on transient errors and return the original response.
                is RefreshResult.TransientError -> return response
            }
            // KMK <--

            response.close()

            val newRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer ${newToken.accessToken}")
                .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    /**
     * Called when the user authenticates with MangaDex for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: MALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        MdUtil.saveOAuth(trackPreferences, mdList, oauth)
    }

    // KMK -->
    /**
     * Attempts to refresh the access token using the stored refresh token.
     *
     * Distinguishes a definitive rejection of the refresh token ([RefreshResult.InvalidGrant]),
     * which requires the user to log in again, from transient failures
     * ([RefreshResult.TransientError]) that must not clear the stored credentials.
     */
    private fun refreshToken(chain: Interceptor.Chain): RefreshResult {
        val currentOauth = oauth ?: return RefreshResult.InvalidGrant

        // If the refresh token is known to be expired there is no point in trying.
        if (currentOauth.isRefreshTokenExpired()) {
            return RefreshResult.InvalidGrant
        }

        return runCatching {
            chain.proceed(MdUtil.refreshTokenRequest(currentOauth)).use { oauthResponse ->
                when {
                    oauthResponse.isSuccessful -> {
                        val newOauth = with(MdUtil.jsonParser) { oauthResponse.parseAs<MALOAuth>() }
                        RefreshResult.Success(newOauth)
                    }
                    // 400/401 with an invalid_grant error means the refresh token is dead.
                    oauthResponse.code in 400..401 &&
                        oauthResponse.peekBody(Long.MAX_VALUE).string().contains("invalid_grant") -> {
                        RefreshResult.InvalidGrant
                    }
                    else -> RefreshResult.TransientError(
                        IOException("Unexpected refresh response: HTTP ${oauthResponse.code}"),
                    )
                }
            }
        }.getOrElse { e ->
            logcat(throwable = e) { "Failed to refresh mangadex oauth" }
            RefreshResult.TransientError(e)
        }
    }

    private sealed interface RefreshResult {
        data class Success(val oauth: MALOAuth) : RefreshResult

        data object InvalidGrant : RefreshResult

        data class TransientError(val cause: Throwable) : RefreshResult
    }
    // KMK <--
}
