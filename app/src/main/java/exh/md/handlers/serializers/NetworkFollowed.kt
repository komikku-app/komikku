package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class NetworkFollowed(
    val code: Int,
    val message: String = "",
    val data: List<FollowedSerializer>? = null
)

@Serializable
data class FollowedSerializer(val mangaId: String, val mangaTitle: String, val followType: Int)
