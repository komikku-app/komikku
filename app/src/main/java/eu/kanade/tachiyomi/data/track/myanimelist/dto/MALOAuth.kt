package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALOAuth(
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    // KMK -->
    // Lifetime (in seconds) of the refresh token, when known. A value of 0 means
    // "unknown / never" (e.g. offline tokens) and is treated as not expiring.
    @SerialName("refresh_expires_in")
    @EncodeDefault
    val refreshExpiresIn: Long = 0,
    // KMK <--
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    // Assumes expired a minute earlier
    private val adjustedExpiresIn: Long = (expiresIn - 60)

    fun isExpired() = createdAt + adjustedExpiresIn < System.currentTimeMillis() / 1000

    // KMK -->
    /**
     * Whether the refresh token itself is known to be expired. Returns false when
     * [refreshExpiresIn] is 0 (unknown or offline token that does not expire by idle).
     */
    fun isRefreshTokenExpired(): Boolean {
        if (refreshExpiresIn <= 0) return false
        return createdAt + refreshExpiresIn < System.currentTimeMillis() / 1000
    }
    // KMK <--
}
