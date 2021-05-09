package exh.md.network

import exh.log.xLogD
import exh.log.xLogI
import exh.log.xLogW
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
        return if (token != null) {
            response.request.newBuilder().header("Authorization", token).build()
        } else {
            // throw Exception("Unable to authenticate request, please re login")
            null
        }
    }

    @Synchronized
    fun refreshToken(loginHelper: MangaDexLoginHelper): String? {
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
                this@TokenAuthenticator.xLogD("Session token does not exist")
                false
            }

            if (checkToken) {
                this@TokenAuthenticator.xLogI("Token is valid, other thread must have refreshed it")
                validated = true
            }
            if (validated.not()) {
                this@TokenAuthenticator.xLogI("Token is invalid trying to refresh")
                val result = runCatching {
                    validated = loginHelper.refreshToken(
                        MdUtil.getAuthHeaders(
                            Headers.Builder().build(),
                            loginHelper.preferences,
                            loginHelper.mdList
                        )
                    )
                }
                if (result.isFailure) {
                    result.exceptionOrNull()?.let {
                        this@TokenAuthenticator.xLogW("Error refreshing token", it)
                    }
                }
            }

            if (validated.not()) {
                this@TokenAuthenticator.xLogI("Did not refresh token, trying to login")
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
            else -> null
        }
    }
}
