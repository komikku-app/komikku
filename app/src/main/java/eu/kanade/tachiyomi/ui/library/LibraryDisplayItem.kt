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

internal fun trackerCoverId(
    trackerId: Long,
    remoteId: Long,
): Long {
    return Long.MIN_VALUE or ((trackerId and 0x7FL) shl 56) or (remoteId and 0x00FFFFFFFFFFFFFFL)
}
