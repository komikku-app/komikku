package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.model.TrackSearch

interface TrackerWithNotInLibrary : Tracker {

    suspend fun getNotInLibraryEntries(): List<TrackSearch>
}
