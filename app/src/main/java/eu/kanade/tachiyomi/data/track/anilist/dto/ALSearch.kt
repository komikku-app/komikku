package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALSearchResult(
    val data: ALSearchPage,
)

@Serializable
data class ALSearchPage(
    @SerialName("Page")
    val page: ALSearchMedia,
)

@Serializable
data class ALSearchMedia(
    val media: List<ALSearchItem>,
)

// SY -->
@Serializable
data class ALIdSearchResult(
    val data: ALIdSearchMedia,
)

@Serializable
data class ALIdSearchMedia(
    @SerialName("Media")
    val media: ALSearchItem,
)
// SY <--
