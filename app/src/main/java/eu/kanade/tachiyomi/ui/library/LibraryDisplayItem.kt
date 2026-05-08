package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.domain.manga.model.MangaCover

sealed interface LibraryDisplayItem {
    val key: String

    data class Local(val item: LibraryItem) : LibraryDisplayItem {
        override val key: String = item.id.toString()
    }

    data class RemoteTrack(val item: RemoteTrackerLibraryItem) : LibraryDisplayItem {
        override val key: String = item.key
    }
}

data class RemoteTrackerTrack(
    val trackerId: Long,
    val remoteId: Long,
    val title: String,
    val coverUrl: String,
    val status: Long,
    val lastChapterRead: Double,
    val totalChapters: Long,
    val trackingUrl: String,
)

data class RemoteTrackerLibraryItem(
    val track: RemoteTrackerTrack,
    val trackerName: String,
    val trackerShortName: String,
    val statusText: String?,
    val progressText: String?,
) {
    val key: String = "${track.trackerId}:${track.remoteId}"

    val coverData = MangaCover(
        mangaId = trackerCoverId(track.trackerId, track.remoteId),
        sourceId = track.trackerId,
        isMangaFavorite = false,
        ogUrl = track.coverUrl,
        lastModified = 0L,
    )
}

internal fun TrackSearch.toRemoteTrackerTrack(): RemoteTrackerTrack {
    return RemoteTrackerTrack(
        trackerId = tracker_id,
        remoteId = remote_id,
        title = title,
        coverUrl = cover_url,
        status = status,
        lastChapterRead = last_chapter_read,
        totalChapters = total_chapters,
        trackingUrl = tracking_url,
    )
}

internal fun RemoteTrackerTrack.toTrackSearch(): TrackSearch {
    return TrackSearch.create(trackerId).apply {
        remote_id = this@toTrackSearch.remoteId
        title = this@toTrackSearch.title
        status = this@toTrackSearch.status
        last_chapter_read = this@toTrackSearch.lastChapterRead
        total_chapters = this@toTrackSearch.totalChapters
        tracking_url = this@toTrackSearch.trackingUrl
    }
}

/**
 * Packs tracker and remote ids into a stable synthetic cover id.
 *
 * Layout:
 * - sign bit set to 1 to keep synthetic ids separate from local ids
 * - 7 bits for tracker id
 * - 56 bits for remote id
 */
internal fun trackerCoverId(
    trackerId: Long,
    remoteId: Long,
): Long {
    require(trackerId in 0L..TRACKER_COVER_ID_TRACKER_MASK) {
        "trackerId must fit in 7 bits, was $trackerId"
    }
    require(remoteId in 0L..TRACKER_COVER_ID_REMOTE_MASK) {
        "remoteId must fit in 56 bits, was $remoteId"
    }

    return TRACKER_COVER_ID_PREFIX or
        ((trackerId and TRACKER_COVER_ID_TRACKER_MASK) shl TRACKER_COVER_ID_TRACKER_SHIFT) or
        (remoteId and TRACKER_COVER_ID_REMOTE_MASK)
}

private val TRACKER_COVER_ID_PREFIX = Long.MIN_VALUE
private const val TRACKER_COVER_ID_TRACKER_SHIFT = 56
private const val TRACKER_COVER_ID_TRACKER_MASK = 0x7FL
private const val TRACKER_COVER_ID_REMOTE_MASK = 0x00FFFFFFFFFFFFFFL
