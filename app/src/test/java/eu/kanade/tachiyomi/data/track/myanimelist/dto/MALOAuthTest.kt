package eu.kanade.tachiyomi.data.track.myanimelist.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MALOAuthTest {

    private val now get() = System.currentTimeMillis() / 1000

    private fun oauth(
        expiresIn: Long = 3600,
        refreshExpiresIn: Long = 0,
        createdAt: Long = now,
    ) = MALOAuth(
        tokenType = "Bearer",
        refreshToken = "refresh",
        accessToken = "access",
        expiresIn = expiresIn,
        refreshExpiresIn = refreshExpiresIn,
        createdAt = createdAt,
    )

    @Test
    fun `access token freshly issued is not expired`() {
        assertFalse(oauth(expiresIn = 3600).isExpired())
    }

    @Test
    fun `access token issued long ago is expired`() {
        assertTrue(oauth(expiresIn = 3600, createdAt = now - 7200).isExpired())
    }

    @Test
    fun `refresh token with unknown lifetime never expires`() {
        // refreshExpiresIn == 0 represents an offline token / unknown lifetime.
        assertFalse(oauth(refreshExpiresIn = 0, createdAt = now - 1_000_000).isRefreshTokenExpired())
    }

    @Test
    fun `refresh token within its lifetime is not expired`() {
        assertFalse(oauth(refreshExpiresIn = 86_400, createdAt = now).isRefreshTokenExpired())
    }

    @Test
    fun `refresh token past its lifetime is expired`() {
        assertTrue(oauth(refreshExpiresIn = 3600, createdAt = now - 7200).isRefreshTokenExpired())
    }
}
