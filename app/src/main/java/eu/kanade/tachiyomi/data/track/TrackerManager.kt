package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.track.service.TrackPreferences // KMK --> // KMK <--
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.combine
import uy.kohesive.injekt.injectLazy // KMK --> // KMK <--

class TrackerManager {

    // KMK -->
    private val trackPreferences: TrackPreferences by injectLazy()
    private val trackerOrderLock = Any()
    // KMK <--

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L

        // SY --> Mangadex from Neko
        const val MDLIST = 60L
        // SY <--
    }

    val mdList = MdList(MDLIST)

    val myAnimeList = MyAnimeList(1L)
    val aniList = Anilist(ANILIST)
    val kitsu = Kitsu(KITSU)
    val shikimori = Shikimori(4L)
    val bangumi = Bangumi(5L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(KAVITA)
    val suwayomi = Suwayomi(9L)

    val trackers =
        listOf(mdList, myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi)

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }

    // KMK -->
    fun orderedTrackers(): List<Tracker> {
        val savedIds = trackPreferences.trackerOrder().get()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
        if (savedIds.isEmpty()) return trackers

        val trackersById = trackers.associateBy { it.id }
        val ordered = savedIds.mapNotNull { trackersById[it] }.distinctBy { it.id }
        val orderedIds = ordered.mapTo(mutableSetOf()) { it.id }
        return ordered + trackers.filterNot { it.id in orderedIds }
    }

    /**
     * Moves a single tracker in the persisted order.
     * Always starts from the latest stored order so simultaneous reorder screens cannot
     * overwrite each other's changes with stale full-list snapshots.
     */
    fun moveTracker(movedTrackerId: Long, toIndex: Int) {
        synchronized(trackerOrderLock) {
            val current = orderedTrackers().toMutableList()
            val fromIndex = current.indexOfFirst { it.id == movedTrackerId }
            if (fromIndex != -1 && toIndex in current.indices) {
                val tracker = current.removeAt(fromIndex)
                current.add(toIndex, tracker)
                trackPreferences.trackerOrder().set(current.joinToString(",") { it.id.toString() })
            }
        }
    }
    // KMK <--
}
