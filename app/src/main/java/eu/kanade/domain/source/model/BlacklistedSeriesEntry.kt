package eu.kanade.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class BlacklistedSeriesEntry(
    val originalTitle: String,
    val normalizedTitle: String,
    val addedAt: Long = 0L,
)
