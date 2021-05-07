package exh.md.network

import exh.log.xLogD
import exh.log.xLogI
import exh.md.utils.MdUtil
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val loginHelper: MangaDexLoginHelper) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        xLogI("Detected Auth error ${response.code} on ${response.request.url}")
        val token = refreshToken(loginHelper)
        if (token.isEmpty()) {
            return null
            throw Exception("Unable to authenticate request, please re login")
        }
        return response.request.newBuilder().header("Authorization", token).build()
    }

    @Synchronized
    fun refreshToken(loginHelper: MangaDexLoginHelper): String {
        var validated = false

        runBlocking {
            val checkToken = try {
                loginHelper.isAuthenticated(
                    MdUtil.getAuthHeaders(
                        Headers.Builder().build(),
                        loginHelper.preferences,
                        loginHelper.mdList
                    )
                )
            } catch (e: NoSessionException) {
                xLogD("Session token does not exist")
                false
            }

            if (checkToken) {
                xLogI("Token is valid, other thread must have refreshed it")
                validated = true
            }
            if (validated.not()) {
                xLogI("Token is invalid trying to refresh")
                validated = loginHelper.refreshToken(
                    MdUtil.getAuthHeaders(
                        Headers.Builder().build(), loginHelper.preferences, loginHelper.mdList
                    )
                )
            }

            if (validated.not()) {
                xLogI("Did not refresh token, trying to login")
                val loginResult = loginHelper.login()
                validated = if (loginResult is MangaDexLoginHelper.LoginResult.Success) {
                    MdUtil.updateLoginToken(
                        loginResult.token,
                        loginHelper.preferences,
                        loginHelper.mdList
                    )
                    true
                } else false
            }
        }
        return when {
            validated -> "bearer: ${MdUtil.sessionToken(loginHelper.preferences, loginHelper.mdList)!!}"
            else -> ""
        }
    }
}
