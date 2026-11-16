package app.komikku.desktop.ui.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId

/**
 * Desktop tracking sheet state (Android `TrackInfoDialogHomeScreen` + its sub-screens' models): the
 * logged-in trackers (enhanced ones only when they accept the manga's source) with the manga's track,
 * search + register, status / chapter / score / dates through the borrowed trackers' `setRemote*`
 * (which also store the track), remove (`DeleteTrack`), refresh (`RefreshTracks`).
 */
class TrackingScreenModel(
    private val mangaId: Long,
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val deleteTrack: DeleteTrack = Injekt.get(),
    private val refreshTracks: RefreshTracks = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val app: android.app.Application = Injekt.get(),
) : StateScreenModel<TrackingScreenModel.State>(State()) {

    @Immutable
    data class Item(val tracker: Tracker, val track: Track?)

    @Immutable
    data class Search(
        val tracker: Tracker,
        val query: String,
        val results: List<TrackSearch>? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )

    @Immutable
    data class State(val items: List<Item> = emptyList(), val search: Search? = null, val refreshing: Boolean = false)

    init {
        screenModelScope.launch {
            val source = withIOContext { getManga.await(mangaId)?.source?.let(sourceManager::getOrStub) }
            combine(getTracks.subscribe(mangaId), trackerManager.loggedInTrackersFlow()) { tracks, trackers ->
                trackers
                    .filter { it !is EnhancedTracker || (source != null && it.accept(source)) }
                    .map { tracker -> Item(tracker, tracks.find { it.trackerId == tracker.id }) }
            }.collectLatest { items -> mutableState.update { it.copy(items = items) } }
        }
    }

    fun startSearch(tracker: Tracker, query: String) {
        mutableState.update { it.copy(search = Search(tracker, query)) }
        search(query)
    }

    fun search(query: String) {
        val search = state.value.search ?: return
        mutableState.update { it.copy(search = search.copy(query = query, loading = true, error = null)) }
        screenModelScope.launch {
            val result = withIOContext { runCatching { search.tracker.search(query) } }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            mutableState.update { state ->
                state.copy(
                    search = state.search?.copy(
                        loading = false,
                        results = result.getOrNull(),
                        error = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName },
                    ),
                )
            }
        }
    }

    fun closeSearch() = mutableState.update { it.copy(search = null) }

    /** Android `registerTracking`: the tracker binds the entry (`AddTracks.bind`) and stores it. */
    fun register(item: TrackSearch) {
        val tracker = state.value.search?.tracker ?: return
        closeSearch()
        screenModelScope.launch { withIOContext { tracker.register(item, mangaId) } }
    }

    private fun remote(item: Item, block: suspend Tracker.(eu.kanade.tachiyomi.data.database.models.Track) -> Unit) {
        val track = item.track ?: return
        screenModelScope.launch { withIOContext { item.tracker.block(track.toDbTrack()) } }
    }

    fun setStatus(item: Item, status: Long) = remote(item) { setRemoteStatus(it, status) }

    fun setChapter(item: Item, chapter: Int) = remote(item) { setRemoteLastChapterRead(it, chapter) }

    fun setScore(item: Item, score: String) = remote(item) { setRemoteScore(it, score) }

    /** [date] = `null` removes the date (Android: 0). */
    fun setStartDate(item: Item, date: LocalDate?) = remote(item) { setRemoteStartDate(it, date.toEpochMillis()) }

    fun setFinishDate(item: Item, date: LocalDate?) = remote(item) { setRemoteFinishDate(it, date.toEpochMillis()) }

    fun remove(item: Item) {
        screenModelScope.launch { withIOContext { deleteTrack.await(mangaId, item.tracker.id) } }
    }

    /** Android: refresh the manga's tracks from the trackers when the sheet opens. */
    fun refresh() {
        mutableState.update { it.copy(refreshing = true) }
        screenModelScope.launch {
            withIOContext {
                refreshTracks.await(mangaId)
                    .filter { it.first != null }
                    .forEach { (tracker, error) ->
                        app.toast(app.stringResource(MR.strings.track_error, tracker!!.name, error.message ?: ""))
                    }
            }
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    private fun LocalDate?.toEpochMillis(): Long = this?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
}
