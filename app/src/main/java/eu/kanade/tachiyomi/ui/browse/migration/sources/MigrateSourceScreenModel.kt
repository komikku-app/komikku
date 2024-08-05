package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSourceScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val setMigrateSorting: SetMigrateSorting = Injekt.get(),
) : StateScreenModel<MigrateSourceScreenModel.State>(State()) {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                getSourcesWithFavoriteCount.subscribe(),
            ) { searchQuery, sourceCounts ->
                val queryFilter: (String?) -> ((Pair<Source, Long>) -> Boolean) = { query ->
                    filter@{ pair ->
                        val source = pair.first
                        if (query.isNullOrBlank()) return@filter true
                        query.split(",").any {
                            val input = it.trim()
                            if (input.isEmpty()) return@any false
                            source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                                source.name.contains(input, ignoreCase = true) ||
                                source.id == input.toLongOrNull()
                        }
                    }
                }
                sourceCounts.filter(queryFilter(searchQuery))
            }
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = sources.toImmutableList(),
                        )
                    }
                }
        }

        preferences.migrationSortingDirection().changes()
            .onEach { mutableState.update { state -> state.copy(sortingDirection = it) } }
            .launchIn(screenModelScope)

        preferences.migrationSortingMode().changes()
            .onEach { mutableState.update { state -> state.copy(sortingMode = it) } }
            .launchIn(screenModelScope)
    }

    fun toggleSortingMode() {
        with(state.value) {
            val newMode = when (sortingMode) {
                SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
            }

            setMigrateSorting.await(newMode, sortingDirection)
        }
    }

    fun toggleSortingDirection() {
        with(state.value) {
            val newDirection = when (sortingDirection) {
                SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
            }

            setMigrateSorting.await(sortingMode, newDirection)
        }
    }

    // KMK -->
    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<Pair<Source, Long>> = persistentListOf(),
        val sortingMode: SetMigrateSorting.Mode = SetMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetMigrateSorting.Direction = SetMigrateSorting.Direction.ASCENDING,
        // KMK -->
        val searchQuery: String? = null,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object FailedFetchingSourcesWithCount : Event
    }
}
