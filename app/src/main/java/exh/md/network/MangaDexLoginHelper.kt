package exh.md.network

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.xLogE
import exh.log.xLogI
import exh.md.dto.LoginRequestDto
import exh.md.dto.RefreshTokenDto
import exh.md.service.MangaDexAuthService
import exh.md.utils.MdUtil
import kotlinx.serialization.encodeToString

class MangaDexLoginHelper(val authServiceLazy: Lazy<MangaDexAuthService>, val preferences: PreferencesHelper, val mdList: MdList) {
    val authService by authServiceLazy
    suspend fun isAuthenticated(): Boolean {
        return runCatching { authService.checkToken().isAuthenticated }
            .getOrElse { e ->
                xLogE("error authenticating", e)
                false
            }
    }

    suspend fun refreshToken(): Boolean {
        val refreshToken = MdUtil.refreshToken(preferences, mdList)
        if (refreshToken.isNullOrEmpty()) {
            return false
        }
        val refresh = runCatching {
            val jsonResponse = authService.refreshToken(RefreshTokenDto(refreshToken))
            preferences.trackToken(mdList).set(MdUtil.jsonParser.encodeToString(jsonResponse.token))
        }
        return refresh.isSuccess
    }

    suspend fun login(
        username: String,
        password: String,
    ): Boolean {
        return withIOContext {
            val loginRequest = LoginRequestDto(username, password)
            val loginResult = runCatching { authService.login(loginRequest) }

            val loginResponseDto = loginResult.getOrNull()
            MdUtil.updateLoginToken(
                loginResponseDto?.token,
                preferences,
                mdList
            )
            loginResponseDto != null
        }
    }

    suspend fun login(): Boolean {
        val username = preferences.trackUsername(mdList)
        val password = preferences.trackPassword(mdList)
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            xLogI("No username or password stored, can't login")
            return false
        }
        return login(username, password)
    }

    suspend fun logout() {
        return withIOContext {
            authService.logout()
        }
    }
}
