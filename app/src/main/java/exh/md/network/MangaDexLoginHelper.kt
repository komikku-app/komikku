package exh.md.network

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

class MangaDexLoginHelper(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val preferences: TrackPreferences,
    private val mdList: MdList,
    private val mangaDexAuthInterceptor: MangaDexAuthInterceptor,
) {

    /**
     *  Login given the generated authorization code
     */
    suspend fun login(authorizationCode: String): Boolean {
        val loginFormBody = FormBody.Builder()
            .add("client_id", MdConstants.Login.clientId)
            .add("grant_type", MdConstants.Login.authorizationCode)
            .add("code", authorizationCode)
            .add("code_verifier", MdUtil.getPkceChallengeCode())
            .add("redirect_uri", MdConstants.Login.redirectUri)
            .build()

        val error = kotlin.runCatching {
            val data = with(MdUtil.jsonParser) {
                client.newCall(
                    POST(MdApi.baseAuthUrl + MdApi.token, headers = headers, body = loginFormBody),
                ).awaitSuccess().parseAs<MALOAuth>()
            }
            mangaDexAuthInterceptor.setAuth(data)
        }.exceptionOrNull()

        return when (error == null) {
            true -> {
                // KMK -->
                // Logged in again: an earlier "session expired" notice is stale now.
                mdList.sessionNotifier.dismiss()
                // KMK <--
                true
            }
            false -> {
                logcat(LogPriority.ERROR, error) { "Error logging in" }
                mdList.logout()
                false
            }
        }
    }

    // KMK -->
    /**
     * Logs out of MangaDex. Logging out locally always succeeds: the stored credentials are cleared first, so
     * the app shows the user as logged out at once, even when offline or when the auth server fails or hangs.
     *
     * Revoking the session on the server is best effort: it is attempted afterwards with the credentials read
     * before clearing them, bounded by [SERVER_LOGOUT_TIMEOUT], and a failure is only logged. A session that
     * could not be revoked is left to expire on the server on its own.
     *
     * @return always true. Kept as a Boolean so existing callers compile unchanged.
     */
    // KMK <--
    suspend fun logout(): Boolean {
        val oauth = MdUtil.loadOAuth(preferences, mdList)
        val sessionToken = oauth?.accessToken
        val refreshToken = oauth?.refreshToken
        // KMK -->
        // Log out locally before calling the server, so logging out never depends on the network. This also
        // clears the auth interceptor's credentials and deletes the stored token.
        mdList.logout()

        if (!refreshToken.isNullOrEmpty() && !sessionToken.isNullOrEmpty()) {
            revokeServerSession(sessionToken, refreshToken)
        }
        return true
    }

    /**
     * Best effort: asks the auth server to end the session of [refreshToken]. Failures and timeouts are logged,
     * not thrown; only cancellation is rethrown.
     */
    private suspend fun revokeServerSession(sessionToken: String, refreshToken: String) {
        val formBody = FormBody.Builder()
            .add("client_id", MdConstants.Login.clientId)
            .add("refresh_token", refreshToken)
            .add("redirect_uri", MdConstants.Login.redirectUri)
            .build()

        try {
            val revoked = withTimeoutOrNull(SERVER_LOGOUT_TIMEOUT) {
                client.newCall(
                    POST(
                        url = MdApi.baseAuthUrl + MdApi.logout,
                        headers = Headers.Builder().add("Authorization", "Bearer $sessionToken")
                            .build(),
                        body = formBody,
                    ),
                ).awaitSuccess().close()
            }
            if (revoked == null) {
                logcat(LogPriority.WARN) { "Timed out revoking the MangaDex session, it will expire on its own" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Error revoking the MangaDex session, it will expire on its own" }
        }
    }
    // KMK <--
}

// KMK -->
/** How long a logout waits for the server to revoke the session; the local logout is already done by then. */
private val SERVER_LOGOUT_TIMEOUT = 5.seconds
// KMK <--
