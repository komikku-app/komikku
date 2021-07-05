package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class SimilarMangaDto(
    val id: String,
    val title: Map<String, String>,
    val contentRating: String,
    val matches: List<SimilarMangaMatchListDto>,
    val updatedAt: String,
)

@Serializable
data class SimilarMangaMatchListDto(
    val id: String,
    val title: Map<String, String>,
    val contentRating: String,
    val score: Double,
)
