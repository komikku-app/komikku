package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALError(
    val errors: List<ALErrorItem>? = null,
)

@Serializable
data class ALErrorItem(
    val message: String,
    val status: Int? = null,
)
