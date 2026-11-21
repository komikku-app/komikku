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
     * so the user can be told to log in again. Not called on logout, nor when a logout or a new login replaced
     * the credentials while the rejected refresh was in flight. Runs on an OkHttp thread, outside the state lock
     * (so it never holds up login or logout) but while holding the refresh lock, so other token refreshes wait
     * for it: keep it short. Exceptions it throws are logged and ignored.
     */
    private val onSessionRejected: () -> Unit = {},
    /** Monotonic clock in nanoseconds, timing [REFRESH_FAILURE_COOLDOWN]. Replaced in tests. */
    private val nanoTime: () -> Long = System::nanoTime,
    // KMK <--
) : Interceptor {

    // KMK -->
    // A single instance (held by MdList) is shared by concurrent OkHttp dispatcher threads and by login and
    // logout, which go through setAuth. It uses two locks, always taken in this order and never the reverse:
    // 1. refreshLock serializes token refreshes and is held across the token endpoint call, so that concurrent
    //    requests hitting the same expired token trigger a single refresh (see refreshToken).
    // 2. This interceptor's monitor, the state lock, guards the credentials (oauth, token, lastRefreshFailure).
    //    It is only held for short sections that never do network I/O, so that setAuth, and therefore login and
    //    logout, never waits for a refresh in flight, which can take up to OkHttp's call timeout.

    /** Serializes token refreshes. Taken before the state lock, never while holding it. */
    private val refreshLock = Any()

    /** Written under the state lock only. */
    @Volatile
    var token = trackPreferences.trackToken(mdList).get().nullIfBlank()

    /** Written under the state lock only; read without it on the fast path of [loadOAuth]. */
    @Volatile
    private var oauth: MALOAuth? = null

    /** The last transient refresh failure, until [setAuth] clears it. Guarded by the state lock. */
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
                // Credentials were already cleared (rejection or logout), and are kept on transient errors.
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
     * refresh token and the oauth object. Forgets the last transient refresh failure, so new credentials are
     * never blocked by its cooldown.
     *
     * Takes only the state lock, never the refresh lock, so it returns at once even while a token refresh is in
     * flight; that refresh then discards its result (see [refreshToken]). [refreshToken] also applies its results
     * through here, reentrantly, while already holding the state lock.
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
     * state lock so that a stale copy read from preferences cannot overwrite newer credentials: every write
     * ([setAuth], which refreshes also go through) updates [oauth] and the preferences together under that lock,
     * so while [oauth] is null under it the preferences hold nothing newer.
     */
    private fun loadOAuth(): MALOAuth? = oauth ?: synchronized(this) {
        oauth ?: MdUtil.loadOAuth(trackPreferences, mdList)?.also { oauth = it }
    }

    /**
     * Replaces [staleOauth], the credentials a request was sent with, by fresh ones and applies the outcome
     * to the stored credentials.
     *
     * Serialized by [refreshLock] so that concurrent requests hitting the same expired token trigger a single
     * refresh: the others wait for it and reuse its result instead of replaying an already used refresh token,
     * which the server may reject and which would then wipe a valid session.
     *
     * The token endpoint call is made without the state lock, so [setAuth] (login, logout) never waits for it.
     * If the credentials are replaced while it is in flight (compared by identity), its result is discarded: a
     * logout or a new login always wins over a refresh of the credentials it replaced, which would otherwise
     * bring back the session the user logged out of, or wipe the new one. The caller then gets the current
     * credentials, or [RefreshResult.Rejected] if they were cleared, and [onSessionRejected] is not called.
     *
     * The stored credentials are only cleared, and [onSessionRejected] called, when the server definitively
     * rejects the refresh token ([RefreshResult.Rejected]: `invalid_grant`, see [isRefreshRejected]). Transient
     * failures ([RefreshResult.TransientError]: no network, timeouts, 429, 5xx, challenge pages, malformed
     * responses, other OAuth errors...) keep them, so a later request can retry the refresh instead of forcing a
     * browser re-login.
     *
     * For [REFRESH_FAILURE_COOLDOWN] after a transient failure, refreshes fail fast with the same error and no
     * network call, so requests queued behind a slow failure (e.g. a hanging auth server) do not each wait out
     * another timeout in turn. A failure of a call its caller canceled (e.g. its coroutine was canceled) says
     * nothing about the server and does not start the cooldown. OkHttp's call timeout also cancels the call, so
     * neither does a refresh it cuts short; the connect and read timeouts, which a hanging auth server normally
     * hits first, do.
     */
    private fun refreshToken(chain: Interceptor.Chain, staleOauth: MALOAuth): RefreshResult {
        synchronized(refreshLock) {
            val latest = synchronized(this) {
                // Cleared by another thread (logout or rejected refresh).
                val latest = oauth ?: return RefreshResult.Rejected
                // Already refreshed (or logged in again) by another thread.
                if (latest.accessToken != staleOauth.accessToken) return RefreshResult.Success(latest)
                // Failed moments ago, most likely while this request was waiting for the refresh lock.
                val recentFailure = lastRefreshFailure
                    ?.takeIf { nanoTime() - it.failedAt < REFRESH_FAILURE_COOLDOWN.inWholeNanoseconds }
                if (recentFailure != null) {
                    logcat { "Skipping MangaDex token refresh, it failed less than $REFRESH_FAILURE_COOLDOWN ago" }
                    return recentFailure.result
                }
                latest
            }

            // Without the state lock, so that login and logout do not wait for the network.
            val result = requestNewToken(chain, latest)
            val canceled = result is RefreshResult.TransientError && chain.call().isCanceled()

            synchronized(this) {
                val current = oauth
                if (current !== latest) {
                    // Logged out or in again while the refresh was in flight: keep the credentials they set.
                    logcat {
                        "MangaDex credentials changed during the token refresh, " +
                            "discarding its result (${result::class.simpleName})"
                    }
                    return if (current != null) RefreshResult.Success(current) else RefreshResult.Rejected
                }
                when (result) {
                    // Both go through setAuth (reentrant, this thread holds the state lock already), which also
                    // clears lastRefreshFailure.
                    is RefreshResult.Success -> setAuth(result.oauth)
                    is RefreshResult.Rejected -> setAuth(null)
                    is RefreshResult.TransientError -> if (canceled) {
                        logcat { "MangaDex token refresh canceled with its call, not starting the cooldown" }
                    } else {
                        lastRefreshFailure = RefreshFailure(nanoTime(), result)
                    }
                }
            }

            // Outside the state lock, so that the callback never holds up login or logout.
            if (result is RefreshResult.Rejected) notifySessionRejected()
            return result
        }
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
 * Whether a failed token endpoint response definitively rejects the refresh token, so the credentials are
 * cleared and the user has to log in again: HTTP 400 (Keycloak's usual code, occasionally 401) with an OAuth
 * error JSON body whose `error` is exactly `invalid_grant` (case-sensitive). That is the only error saying this
 * refresh token or session is dead (expired, revoked, already used...) and that logging in again fixes it.
 *
 * Other OAuth errors (`invalid_client`, `unauthorized_client`, `not_allowed`, `invalid_scope`,
 * `invalid_request`...) are client or server configuration problems that a new login would not fix and that may
 * be temporary on the server's side, so they are transient: the credentials are kept, and the user can still log
 * out manually. So is anything else (403, 429, 5xx, HTML challenge pages, empty or non OAuth bodies).
 */
internal fun isRefreshRejected(code: Int, body: String): Boolean =
    (code == 400 || code == 401) && parseOAuthError(body) == INVALID_GRANT

/** The OAuth error of a refresh token that is no longer valid, see [isRefreshRejected]. */
private const val INVALID_GRANT = "invalid_grant"

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
