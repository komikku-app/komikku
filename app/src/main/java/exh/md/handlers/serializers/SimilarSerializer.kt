package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class SimilarMangaResponse(
    val id: String,
    val title: Map<String, String>,
    val contentRating: String,
    val matches: List<Matches>,
    val updatedAt: String
)

@Serializable
data class Matches(
    val id: String,
    val title: Map<String, String>,
    val contentRating: String,
    val score: Double
)
