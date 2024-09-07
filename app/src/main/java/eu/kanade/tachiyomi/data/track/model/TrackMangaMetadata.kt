package eu.kanade.tachiyomi.data.track.model

data class TrackMangaMetadata(
    val remoteId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val description: String?,
    val authors: String?,
    val artists: String?,
)
