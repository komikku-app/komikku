package eu.kanade.tachiyomi.data.track.animeupdates.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MUContext(
    @SerialName("session_token")
    val sessionToken: String,
    val uid: Long,
)
