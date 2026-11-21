package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.parseAs
import exh.md.utils.MdUtil
import exh.util.nullIfBlank
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.io.IOException

class MangaDexAuthInterceptor(
    private val trackPreferences: TrackPreferences,
    private val mdList: MdList,
) : Interceptor {

    // KMK -->
    // A single instance (held by MdList) is shared by concurrent OkHttp dispatcher threads.
    @Volatile
    var token = trackPreferences.trackToken(mdList).get().nullIfBlank()

    @Volatile
    private var oauth: MALOAuth? = null
    // KMK <--

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // KMK -->
        // Work from a local snapshot: another thread may refresh or clear [oauth] at any time.
        var current = loadOAuth() ?: throw IOException("No authentication token")

        // Refresh access token if expired
        if (current.isExpired()) {
            current = when (val result = refreshToken(chain, current)) {
                is RefreshResult.Success -> result.oauth
                is RefreshResult.Rejected -> throw IOException("MangaDex session expired, please log in again")
                is RefreshResult.TransientError -> throw IOException("Could not refresh MangaDex token", result.cause)
            }
        }

        // Add the authorization header to the original request
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${current.accessToken}")
            .build()
        // KMK <--

        val response = chain.proceed(authRequest)
        val tokenIsExpired = response.headers["www-authenticate"]
            ?.contains("The access token expired") ?: false

        // Retry the request once with a new token in case it was not already refreshed
        // by the is expired check before.
        if (response.code == 401 && tokenIsExpired) {
            // KMK -->
            val newToken = when (val result = refreshToken(chain, current)) {
                is RefreshResult.Success -> result.oauth
                // Credentials were already cleared on rejection, and are kept on transient errors.
                is RefreshResult.Rejected, is RefreshResult.TransientError -> return response
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

    // KMK -->
    /**
     * Called when the user authenticates with MangaDex for the first time (or logs out, with null). Sets the
     * refresh token and the oauth object. Synchronized with [refreshToken], which also goes through here.
     */
    @Synchronized
    fun setAuth(oauth: MALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        MdUtil.saveOAuth(trackPreferences, mdList, oauth)
    }

    /**
     * Returns the in-memory credentials, loading them from preferences on first use. Loading is done under the
     * lock so that a stale copy read from preferences cannot overwrite credentials refreshed concurrently.
     */
    private fun loadOAuth(): MALOAuth? = oauth ?: synchronized(this) {
        oauth ?: MdUtil.loadOAuth(trackPreferences, mdList)?.also { oauth = it }
    }

    /**
     * Replaces [staleOauth], the credentials a request was sent with, by fresh ones and applies the outcome
     * to the stored credentials.
     *
     * Serialized so that concurrent requests hitting the same expired token trigger a single refresh: the
     * others reuse its result instead of replaying an already used refresh token, which the server may reject
     * and which would then wipe a valid session.
     *
     * The stored credentials are only cleared when the server definitively rejects the refresh token
     * ([RefreshResult.Rejected]). Transient failures ([RefreshResult.TransientError]: no network, 429, 5xx,
     * challenge pages, malformed responses...) keep them, so a later request can retry the refresh instead of
     * forcing a browser re-login.
     */
    @Synchronized
    private fun refreshToken(chain: Interceptor.Chain, staleOauth: MALOAuth): RefreshResult {
        // Cleared by another thread (logout or rejected refresh).
        val latest = oauth ?: return RefreshResult.Rejected
        // Already refreshed (or logged in again) by another thread.
        if (latest.accessToken != staleOauth.accessToken) return RefreshResult.Success(latest)

        val result = requestNewToken(chain, latest)
        when (result) {
            is RefreshResult.Success -> setAuth(result.oauth)
            is RefreshResult.Rejected -> setAuth(null)
            is RefreshResult.TransientError -> Unit
        }
        return result
    }

    private fun requestNewToken(chain: Interceptor.Chain, current: MALOAuth): RefreshResult {
        return try {
            chain.proceed(MdUtil.refreshTokenRequest(current)).use { response ->
                if (response.isSuccessful) {
                    val newOauth = try {
                        with(MdUtil.jsonParser) { response.parseAs<MALOAuth>() }
                    } catch (e: IllegalArgumentException) {
                        // Drop the cause: serialization messages may echo the body, which holds tokens.
                        throw IOException("Malformed MangaDex token response (${e::class.simpleName})")
                    }
                    logcat { "Refreshed MangaDex token" }
                    RefreshResult.Success(newOauth)
                } else {
                    val code = response.code
                    val body = response.peekBody(MAX_ERROR_BODY_BYTES).string()
                    val error = parseOAuthError(body)
                    if (isRefreshRejected(code, body)) {
                        logcat(LogPriority.WARN) {
                            "MangaDex refresh token rejected (HTTP $code, error=$error), clearing credentials"
                        }
                        RefreshResult.Rejected
                    } else {
                        logcat(LogPriority.WARN) {
                            "MangaDex token refresh failed (HTTP $code, error=$error), keeping credentials"
                        }
                        RefreshResult.TransientError(IOException("Unexpected refresh response: HTTP $code"))
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "MangaDex token refresh failed, keeping credentials" }
            RefreshResult.TransientError(e)
        }
    }

    private sealed interface RefreshResult {
        data class Success(val oauth: MALOAuth) : RefreshResult

        data object Rejected : RefreshResult

        data class TransientError(val cause: Throwable) : RefreshResult
    }
    // KMK <--
}

// KMK -->
private const val MAX_ERROR_BODY_BYTES = 64 * 1024L

/**
 * Whether a failed token endpoint response definitively rejects the refresh token, so the user has to log in
 * again. Keycloak answers those with HTTP 400 (occasionally 401) and an OAuth error JSON body (`invalid_grant`,
 * `invalid_client`, `not_allowed`...). Anything else (403, 429, 5xx, HTML challenge pages, empty bodies) is
 * transient.
 */
internal fun isRefreshRejected(code: Int, body: String): Boolean =
    (code == 400 || code == 401) && parseOAuthError(body) != null

/** The non-blank string `error` field of an OAuth error JSON body, or null if [body] is not one. */
private fun parseOAuthError(body: String): String? {
    val error = try {
        (MdUtil.jsonParser.parseToJsonElement(body) as? JsonObject)?.get("error")
    } catch (_: Exception) {
        null
    }
    return (error as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
// KMK <--
