package exh.md.network

import exh.log.xLogI
import exh.md.utils.MdUtil
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

class TokenAuthenticator(private val loginHelper: MangaDexLoginHelper) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        xLogI("Detected Auth error ${response.code} on ${response.request.url}")

        val token = try {
            refreshToken(loginHelper)
        } catch (e: Exception) {
            throw IOException(e)
        }
        return if (token != null) {
            response.request.newBuilder().header("Authorization", token).build()
        } else {
            null
        }
    }

    @Synchronized
    fun refreshToken(loginHelper: MangaDexLoginHelper): String? {
        var validated = false

        runBlocking {
            val checkToken = loginHelper.isAuthenticated()

            if (checkToken) {
                this@TokenAuthenticator.xLogI("Token is valid, other thread must have refreshed it")
                validated = true
            }
            if (validated.not()) {
                this@TokenAuthenticator.xLogI("Token is invalid trying to refresh")
                validated = loginHelper.refreshToken()
            }

            if (validated.not()) {
                this@TokenAuthenticator.xLogI("Did not refresh token, trying to login")
                validated = loginHelper.login()
            }
        }
        return when {
            validated -> "Bearer ${MdUtil.sessionToken(loginHelper.preferences, loginHelper.mdList)!!}"
            else -> null
        }
    }
}
