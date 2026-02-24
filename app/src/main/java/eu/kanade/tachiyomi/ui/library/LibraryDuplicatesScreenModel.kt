package eu.kanade.tachiyomi.ui.library

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetAllDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryDuplicatesScreenModel(
    private val getAllDuplicateLibraryManga: GetAllDuplicateLibraryManga = Injekt.get(),
) : StateScreenModel<LibraryDuplicatesScreenModel.State>(State.Loading) {

    init {
        loadDuplicates()
    }

    fun loadDuplicates() {
        screenModelScope.launch {
            mutableState.update { State.Loading }
            try {
                val duplicates = getAllDuplicateLibraryManga()
                mutableState.update {
                    if (duplicates.isEmpty()) {
                        State.Empty
                    } else {
                        State.Success(duplicates)
                    }
                }
            } catch (e: Exception) {
                mutableState.update { State.Error(e) }
            }
        }
    }

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Success(val duplicateGroups: Map<String, List<Manga>>) : State
        data class Error(val error: Throwable) : State
    }
}
