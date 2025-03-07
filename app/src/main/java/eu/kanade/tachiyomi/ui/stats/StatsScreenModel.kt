package eu.kanade.tachiyomi.ui.stats

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.fastCountNot
import eu.kanade.core.util.fastFilterNot
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SAnime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.GetSeenAnimeNotInLibraryView
import tachiyomi.domain.history.interactor.GetTotalWatchDuration
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class StatsScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getTotalWatchDuration: GetTotalWatchDuration = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    // SY -->
    private val getSeenAnimeNotInLibraryView: GetSeenAnimeNotInLibraryView = Injekt.get(),
    // SY <--
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers() }

    // SY -->
    private val _allRead = MutableStateFlow(false)
    val allRead = _allRead.asStateFlow()
    // SY <--

    init {
        // SY -->
        _allRead.onEach { allRead ->
            mutableState.update { StatsScreenState.Loading }
            val libraryManga = getLibraryAnime.await() + if (allRead) {
                getSeenAnimeNotInLibraryView.await()
            } else {
                emptyList()
            }
            // SY <--

            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val mangaTrackMap = getMangaTrackMap(distinctLibraryManga)
            val scoredMangaTrackerMap = getScoredMangaTrackMap(mangaTrackMap)

            val meanScore = getTrackMeanScore(scoredMangaTrackerMap)

            val overviewStatData = StatsData.Overview(
                libraryMangaCount = distinctLibraryManga.size,
                completedMangaCount = distinctLibraryManga.count {
                    it.anime.status.toInt() == SAnime.COMPLETED && it.unseenCount == 0L
                },
                totalReadDuration = getTotalWatchDuration.await(),
            )

            val titlesStatData = StatsData.Titles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryManga),
                startedMangaCount = distinctLibraryManga.count { it.hasStarted },
                localMangaCount = distinctLibraryManga.count { it.anime.isLocal() },
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryManga.sumOf { it.totalEpisodes }.toInt(),
                readChapterCount = distinctLibraryManga.sumOf { it.seenCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = mangaTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            mutableState.update {
                StatsScreenState.Success(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
            // SY -->
        }.launchIn(screenModelScope)
        // SY <--
    }

    private fun getGlobalUpdateItemCount(libraryAnime: List<LibraryAnime>): Int {
        val includedCategories = preferences.updateCategories().get().map { it.toLong() }
        val includedManga = if (includedCategories.isNotEmpty()) {
            libraryAnime.filter { it.category in includedCategories }
        } else {
            libraryAnime
        }

        val excludedCategories = preferences.updateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (excludedCategories.isNotEmpty()) {
            libraryAnime.fastMapNotNull { manga ->
                manga.id.takeIf { manga.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateAnimeRestrictions().get()
        return includedManga
            .fastFilterNot { it.anime.id in excludedMangaIds }
            .fastDistinctBy { it.anime.id }
            .fastCountNot {
                (ANIME_NON_COMPLETED in updateRestrictions && it.anime.status.toInt() == SAnime.COMPLETED) ||
                    (ANIME_HAS_UNSEEN in updateRestrictions && it.unseenCount != 0L) ||
                    (ANIME_NON_SEEN in updateRestrictions && it.totalEpisodes > 0 && !it.hasStarted)
            }
    }

    private suspend fun getMangaTrackMap(libraryAnime: List<LibraryAnime>): Map<Long, List<Track>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryAnime.associate { manga ->
            val tracks = getTracks.await(manga.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            manga.id to tracks
        }
    }

    private fun getScoredMangaTrackMap(mangaTrackMap: Map<Long, List<Track>>): Map<Long, List<Track>> {
        return mangaTrackMap.mapNotNull { (mangaId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            mangaId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredMangaTrackMap: Map<Long, List<Track>>): Double {
        return scoredMangaTrackMap
            .map { (_, tracks) ->
                tracks.map(::get10PointScore).average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private fun get10PointScore(track: Track): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.get10PointScore(track)
    }

    fun toggleReadManga() {
        _allRead.value = !_allRead.value
    }
}
