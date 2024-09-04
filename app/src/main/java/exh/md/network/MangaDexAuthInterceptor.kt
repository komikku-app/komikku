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
        // Refresh access token if expired
        if (oauth != null && oauth!!.isExpired()) {
            setAuth(refreshToken(chain))
        }

        if (oauth == null) {
            throw IOException("No authentication token")
        }

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
            val newToken = refreshToken(chain)
            setAuth(newToken)

            newToken ?: return response
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

    private fun refreshToken(chain: Interceptor.Chain): MALOAuth? {
        val newOauth = runCatching {
            val oauthResponse = chain.proceed(MdUtil.refreshTokenRequest(oauth!!))

            if (oauthResponse.isSuccessful) {
                with(MdUtil.jsonParser) { oauthResponse.parseAs<MALOAuth>() }
            } else {
                oauthResponse.close()
                null
            }
        }

        logcat(throwable = newOauth.exceptionOrNull()) { "Fetched new mangadex oauth" }

        return newOauth.getOrNull()
    }
}
