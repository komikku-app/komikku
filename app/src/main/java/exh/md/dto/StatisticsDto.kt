package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatisticsDto(
    val statistics: Map<String, StatisticsMangaDto>,
)

@Serializable
data class StatisticsMangaDto(
    val rating: StatisticsMangaRatingDto,
    val follows: Int? = null,
    val comments: StatisticsMangaCommentsDto? = null,
)

@Serializable
data class StatisticsMangaRatingDto(
    val average: Double?,
    val bayesian: Double?,
)

@Serializable
data class StatisticsMangaCommentsDto(
    val threadId: Long? = null,
    val repliesCount: Int? = null,
)
