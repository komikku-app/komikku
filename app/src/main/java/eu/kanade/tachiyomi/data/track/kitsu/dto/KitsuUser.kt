package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuCurrentUserResult(
    val data: List<KitsuUser>,
)

@Serializable
data class KitsuUser(
    val id: String,
    // KMK -->
    val attributes: KitsuUserAttributes? = null,
    // KMK <--
)

// KMK -->
@Serializable
data class KitsuUserAttributes(
    // One of `simple`, `regular` or `advanced`; only returned for the authenticated user.
    val ratingSystem: String? = null,
)
// KMK <--
