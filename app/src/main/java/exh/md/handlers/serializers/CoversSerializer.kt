package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ApiCovers(
    val data: List<CoversResult>,
)

@Serializable
data class CoversResult(
    val volume: String,
    val url: String
)
