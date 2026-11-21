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
import kotlin.time.Duration.Companion.seconds

class MangaDexAuthInterceptor(
    private val trackPreferences: TrackPreferences,
    private val mdList: MdList,
    // KMK -->
    /**
     * Called once each time the server definitively rejects the refresh token and the credentials are cleared,
     * so the user can be told to log in again. Not called on logout. Runs on an OkHttp thread, under this
     * interceptor's lock; exceptions it throws are logged and ignored.
     */
    private val onSessionRejected: () -> Unit = {},
    /** Monotonic clock in nanoseconds, timing [REFRESH_FAILURE_COOLDOWN]. Replaced in tests. */
    private val nanoTime: () -> Long = System::nanoTime,
    // KMK <--
) : Interceptor {

    // KMK -->
    // A single instance (held by MdList) is shared by concurrent OkHttp dispatcher threads.
    @Volatile
    var token = trackPreferences.trackToken(mdList).get().nullIfBlank()

    @Volatile
    private var oauth: MALOAuth? = null

    /** The last transient refresh failure, until [setAuth] clears it. Guarded by this interceptor's lock. */
    private var lastRefreshFailure: RefreshFailure? = null
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
     * Forgets the last transient refresh failure, so new credentials are never blocked by its cooldown.
     */
    @Synchronized
    fun setAuth(oauth: MALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        lastRefreshFailure = null
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
     * The stored credentials are only cleared, and [onSessionRejected] called, when the server definitively
     * rejects the refresh token ([RefreshResult.Rejected]). Transient failures ([RefreshResult.TransientError]:
     * no network, 429, 5xx, challenge pages, malformed responses...) keep them, so a later request can retry the
     * refresh instead of forcing a browser re-login.
     *
     * For [REFRESH_FAILURE_COOLDOWN] after a transient failure, refreshes fail fast with the same error and no
     * network call, so requests queued behind a slow failure (e.g. a hanging auth server) do not each wait out
     * another timeout while holding the lock, which would also hold up [setAuth] and therefore logout.
     */
    @Synchronized
    private fun refreshToken(chain: Interceptor.Chain, staleOauth: MALOAuth): RefreshResult {
        // Cleared by another thread (logout or rejected refresh).
        val latest = oauth ?: return RefreshResult.Rejected
        // Already refreshed (or logged in again) by another thread.
        if (latest.accessToken != staleOauth.accessToken) return RefreshResult.Success(latest)
        // Failed moments ago, most likely while this request was waiting for the lock.
        val recentFailure = lastRefreshFailure
            ?.takeIf { nanoTime() - it.failedAt < REFRESH_FAILURE_COOLDOWN.inWholeNanoseconds }
        if (recentFailure != null) {
            logcat { "Skipping MangaDex token refresh, it failed less than $REFRESH_FAILURE_COOLDOWN ago" }
            return recentFailure.result
        }

        val result = requestNewToken(chain, latest)
        when (result) {
            // Both go through setAuth, which also clears lastRefreshFailure.
            is RefreshResult.Success -> setAuth(result.oauth)
            is RefreshResult.Rejected -> {
                setAuth(null)
                notifySessionRejected()
            }
            is RefreshResult.TransientError -> lastRefreshFailure = RefreshFailure(nanoTime(), result)
        }
        return result
    }

    /** Runs [onSessionRejected], which must never break the request that hit the rejection. */
    private fun notifySessionRejected() {
        try {
            onSessionRejected()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to report the rejected MangaDex session" }
        }
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

    /** A transient refresh failure, [failedAt] a [nanoTime] reading taken when it happened. */
    private class RefreshFailure(val failedAt: Long, val result: RefreshResult.TransientError)
    // KMK <--
}

// KMK -->
private const val MAX_ERROR_BODY_BYTES = 64 * 1024L

// Short: just long enough for requests queued behind a slow failure to drain, not to fail retries once back online.
private val REFRESH_FAILURE_COOLDOWN = 10.seconds

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
