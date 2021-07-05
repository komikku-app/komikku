package exh.md.dto

import kotlinx.serialization.Serializable

/**
 * Login Request object for Dex Api
 */
@Serializable
data class LoginRequestDto(val username: String, val password: String)

/**
 * Response after login
 */
@Serializable
data class LoginResponseDto(val result: String, val token: LoginBodyTokenDto)

/**
 * Tokens for the logins
 */
@Serializable
data class LoginBodyTokenDto(val session: String, val refresh: String)

/**
 * Response after logout
 */
@Serializable
data class LogoutDto(val result: String)

/**
 * Check if session token is valid
 */
@Serializable
data class CheckTokenDto(val isAuthenticated: Boolean)

/**
 * Request to  refresh token
 */
@Serializable
data class RefreshTokenDto(val token: String)
