package mihon.feature.upcoming

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingScreenModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : StateScreenModel<UpcomingScreenModel.State>(State()) {
    // KMK -->
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    // KMK <--

    init {
        screenModelScope.launch {
            getUpcomingManga.subscribe().collectLatest {
                mutableState.update { state ->
                    val upcomingItems = it.toUpcomingUIModels()
                    state.copy(
                        items = upcomingItems,
                        events = upcomingItems.toEvents(),
                        headerIndexes = upcomingItems.getHeaderIndexes(),
                    )
                }
            }
        }
        // KMK -->
        screenModelScope.launch {
            mutableState.update { state ->
                val updatingItems = getUpcomingManga.updatingMangas().toUpcomingUIModels()
                state.copy(
                    updatingItems = updatingItems,
                    updatingEvents = updatingItems.toEvents(),
                    updatingHeaderIndexes = updatingItems.getHeaderIndexes(),
                )
            }
        }
        // KMK <--
    }

    private fun List<Manga>.toUpcomingUIModels(): ImmutableList<UpcomingUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingUIModel>.toEvents(): ImmutableMap<LocalDate, Int> {
        return filterIsInstance<UpcomingUIModel.Header>()
            .associate { it.date to it.mangaCount }
            .toImmutableMap()
    }

    private fun List<UpcomingUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
            .toImmutableMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        mutableState.update { it.copy(selectedYearMonth = yearMonth) }
    }

    // KMK -->
    val restriction by lazy { libraryPreferences.autoUpdateMangaRestrictions().get() }

    fun showUpdatingMangas() {
        mutableState.update { state ->
            state.copy(
                isShowingUpdatingMangas = true,
            )
        }
    }

    fun hideUpdatingMangas() {
        mutableState.update { state ->
            state.copy(
                isShowingUpdatingMangas = false,
            )
        }
    }
    // KMK <--

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        // KMK -->
        val isShowingUpdatingMangas: Boolean = false,
        val updatingItems: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val updatingEvents: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val updatingHeaderIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        // KMK <--
    )
}
