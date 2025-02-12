package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String,
    val status: String?,
    val startDate: ALFuzzyDate,
    val volumes: Long?,
    val chapters: Long?,
    val averageScore: Int?,
) {
    fun toALManga(): ALManga = ALManga(
        remoteId = id,
        title = title.userPreferred,
        imageUrl = coverImage.large,
        description = description,
        format = format.replace("_", "-"),
        publishingStatus = status ?: "",
        startDateFuzzy = startDate.toEpochMilli(),
        totalVolumes = volumes ?: 0,
        totalChapters = chapters ?: 0,
        averageScore = averageScore ?: -1,
    )
}

@Serializable
data class ALItemTitle(
    val userPreferred: String,
)

@Serializable
data class ItemCover(
    val large: String,
)
