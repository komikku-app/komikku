package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

/**
 * Login Request object for Dex Api
 */
@Serializable
data class LoginRequest(val username: String, val password: String)

/**
 * Response after login
 */
@Serializable
data class LoginResponse(val result: String, val token: LoginBodyToken)

/**
 * Tokens for the logins
 */
@Serializable
data class LoginBodyToken(val session: String, val refresh: String)

/**
 * Response after logout
 */
@Serializable
data class ResultResponse(val result: String)

/**
 * Check if session token is valid
 */
@Serializable
data class CheckTokenResponse(val isAuthenticated: Boolean)

/**
 * Request to  refresh token
 */
@Serializable
data class RefreshTokenRequest(val token: String)
