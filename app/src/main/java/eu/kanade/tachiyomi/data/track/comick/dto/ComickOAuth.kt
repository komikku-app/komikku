package eu.kanade.tachiyomi.data.track.comick.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// KMK -->
@Serializable
data class ComickOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String = "",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 >= createdAt + expiresIn - 60
}
// KMK <--
