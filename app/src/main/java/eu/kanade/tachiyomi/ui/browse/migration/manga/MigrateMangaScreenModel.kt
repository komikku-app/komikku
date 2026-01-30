package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaScreenModel(
    private val sourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
) : StateScreenModel<MigrateMangaScreenModel.State>(State()) {

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    // KMK -->
    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    // KMK <--

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    mutableState.update { it.copy(titleList = list) }
                }
        }
    }

    fun toggleSelection(
        item: Manga,
        // KMK -->
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        if ((item.id in state.value.selection) == selected) return
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                state.titles.run {
                    val selectedIndex = indexOfFirst { it.id == item.id }
                    if (selectedIndex < 0) return@run

                    val firstSelection = list.isEmpty()
                    if (selected) list.add(item.id) else list.remove(item.id)

                    if (selected && fromLongPress) {
                        if (firstSelection) {
                            selectedPositions[0] = selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Try to select the items in-between when possible
                            val range: IntRange
                            if (selectedIndex < selectedPositions[0]) {
                                range = selectedIndex + 1 until selectedPositions[0]
                                selectedPositions[0] = selectedIndex
                            } else if (selectedIndex > selectedPositions[1]) {
                                range = (selectedPositions[1] + 1) until selectedIndex
                                selectedPositions[1] = selectedIndex
                            } else {
                                // Just select itself
                                range = IntRange.EMPTY
                            }

                            range.forEach {
                                val inBetweenItem = get(it)
                                if (inBetweenItem.id !in list) {
                                    list.add(inBetweenItem.id)
                                }
                            }
                        }
                    } else if (!fromLongPress) {
                        if (!selected) {
                            if (selectedIndex == selectedPositions[0]) {
                                selectedPositions[0] = indexOfFirst { it.id in list }
                            } else if (selectedIndex == selectedPositions[1]) {
                                selectedPositions[1] = indexOfLast { it.id in list }
                            }
                        } else {
                            if (selectedIndex < selectedPositions[0]) {
                                selectedPositions[0] = selectedIndex
                            } else if (selectedIndex > selectedPositions[1]) {
                                selectedPositions[1] = selectedIndex
                            }
                        }
                    }
                }
            }
            // KMK <--
            state.copy(selection = selection)
        }
    }

    // KMK -->
    fun toggleAllSelection(selected: Boolean = true) {
        mutableState.update { state ->
            val selection = if (selected) {
                state.titles.mapTo(mutableSetOf()) { it.id }
            } else {
                emptySet()
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            state.copy(selection = selection)
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                state.titles.forEach { item ->
                    if (!list.remove(item.id)) list.add(item.id)
                }
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            state.copy(selection = selection)
        }
    }
    // KMK <--

    fun clearSelection() {
        // KMK -->
        toggleAllSelection(false)
        // KMK <--
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        private val titleList: ImmutableList<Manga>? = null,
    ) {

        val titles: ImmutableList<Manga>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
