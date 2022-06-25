package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatisticsDto(
    val statistics: Map<String, StatisticsMangaDto>,
)

@Serializable
data class StatisticsMangaDto(
    val rating: StatisticsMangaRatingDto,
)

@Serializable
data class StatisticsMangaRatingDto(
    val average: Double?,
    val bayesian: Double?,
)
