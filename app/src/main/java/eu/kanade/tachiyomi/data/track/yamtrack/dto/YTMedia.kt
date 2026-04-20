package eu.kanade.tachiyomi.data.track.yamtrack.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.yamtrack.Yamtrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YTSearchResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<YTSearchItem> = emptyList(),
)

@Serializable
data class YTSearchItem(
    @SerialName("media_id")
    val mediaId: String = "",
    val source: String = "",
    val title: String = "",
    val image: String? = null,
    @SerialName("media_type")
    val mediaType: String? = null,
    val description: String? = null,
)

@Serializable
data class YTMediaItem(
    @SerialName("media_id")
    val mediaId: String? = null,
    val source: String? = null,
    val title: String? = null,
    val image: String? = null,
    @SerialName("media_type")
    val mediaType: String? = null,
    val synopsis: String? = null,
    val tracked: Boolean = false,
    @SerialName("max_progress")
    val maxProgress: Int? = null,
    val consumptions: List<YTConsumption> = emptyList(),
)

@Serializable
data class YTConsumption(
    val status: Int? = null,
    val progress: Int? = null,
    val score: Double? = null,
)

fun YTSearchItem.toTrackSearch(trackerId: Long, baseUrl: String): TrackSearch {
    val item = this
    return TrackSearch.create(trackerId).apply {
        remote_id = Yamtrack.buildRemoteId(item.source, item.mediaId)
        title = item.title
        cover_url = item.image.orEmpty()
        summary = item.description.orEmpty()
        tracking_url = Yamtrack.buildTrackingUrl(baseUrl, item.source, item.mediaId)
        publishing_type = item.mediaType.orEmpty()
    }
}

fun YTMediaItem.copyToTrack(track: Track) {
    val consumption = consumptions.firstOrNull()
    track.status = Yamtrack.statusFromApi(consumption?.status)
    track.last_chapter_read = consumption?.progress?.toDouble() ?: track.last_chapter_read
    track.score = consumption?.score ?: 0.0
    maxProgress?.let { track.total_chapters = it.toLong() }
}
