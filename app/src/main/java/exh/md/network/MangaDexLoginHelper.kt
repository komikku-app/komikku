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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class MangaDexLoginHelper(authServiceLazy: Lazy<MangaDexAuthService>, val preferences: PreferencesHelper, val mdList: MdList) {
    private val authService by authServiceLazy
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
            MdUtil.updateLoginToken(jsonResponse.token, preferences, mdList)
        }

        val e = refresh.exceptionOrNull()
        if (e is CancellationException) throw e

        return refresh.isSuccess
    }

    suspend fun login(
        username: String,
        password: String,
    ): Boolean {
        return withIOContext {
            val loginRequest = LoginRequestDto(username, password)
            val loginResult = runCatching { authService.login(loginRequest) }
                .onFailure { this@MangaDexLoginHelper.xLogE("Error logging in", it) }

            val e = loginResult.exceptionOrNull()
            if (e is CancellationException) throw e

            val loginResponseDto = loginResult.getOrNull()
            if (loginResponseDto != null) {
                MdUtil.updateLoginToken(
                    loginResponseDto.token,
                    preferences,
                    mdList,
                )
                true
            } else {
                false
            }
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
            withTimeoutOrNull(10.seconds) {
                runCatching {
                    authService.logout()
                }.onFailure {
                    if (it is CancellationException) throw it
                    this@MangaDexLoginHelper.xLogE("Error logging out", it)
                }
            }
        }
    }
}
