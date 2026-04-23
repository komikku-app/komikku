package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch

suspend fun bindTrackSearchToManga(
    trackSearch: TrackSearch,
    mangaId: Long,
    trackerManager: TrackerManager,
    addTracks: AddTracks,
): Boolean {
    val tracker = trackerManager.get(trackSearch.tracker_id) ?: return false

    return try {
        addTracks.bind(tracker, trackSearch, mangaId)
        true
    } catch (e: Exception) {
        false
    }
}
