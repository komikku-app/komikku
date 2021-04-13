package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source

interface LoginSource : Source {
    val requiresLogin: Boolean

    val twoFactorAuth: AuthSupport

    fun isLogged(): Boolean

    fun getUsername(): String

    fun getPassword(): String

    suspend fun login(username: String, password: String, twoFactorCode: String?): Boolean

    suspend fun logout(): Boolean

    enum class AuthSupport {
        NOT_SUPPORTED,
        SUPPORTED,
        REQUIRED
    }
}
