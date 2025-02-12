package eu.kanade.tachiyomi.ui.errors

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.presentation.errors.components.ErrorUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.data.error.databaseErrorMapper
import tachiyomi.domain.error.interactor.GetDatabaseError
import tachiyomi.domain.error.model.DatabaseError
import tachiyomi.domain.error.model.DatabaseErrorType
import tachiyomi.domain.manga.interactor.GetMangaBySource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DatabaseErrorScreenModel(
    private val getDatabaseError: GetDatabaseError = Injekt.get(),
    private val getMangaBySource: GetMangaBySource = Injekt.get(),
) : StateScreenModel<DatabaseErrorScreenState>(DatabaseErrorScreenState()) {

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedErrorIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            getDatabaseError.count()
                .collectLatest { errors ->
                    val sourceIds = errors.map { error -> error.sourceId }.distinct()
                    val errorMangasBySource = getMangaBySource.await(sourceIds)
                    val duplicateManga = errors
                        .groupBy { it.sourceId }
                        .mapNotNull { (sourceId, errorCountList) ->
                            val urlList = errorCountList.map { errorCount -> errorCount.url }
                            errorMangasBySource[sourceId]?.filter { it.url in urlList }
                        }
                        .flatten()
                        .map {
                            databaseErrorMapper(
                                mangaId = it.id,
                                mangaTitle = it.title,
                                mangaSource = it.source,
                                favorite = it.favorite,
                                mangaThumbnail = it.thumbnailUrl,
                                coverLastModified = it.coverLastModified,
                                errorType = DatabaseErrorType.DUPLICATE_MANGA_URL,
                            )
                        }
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = toDatabaseErrorItems(duplicateManga),
                            messages = listOf("errorMessages"),
                        )
                    }
                }
        }
    }

    private fun toDatabaseErrorItems(errors: List<DatabaseError>): List<ErrorItem> {
        return errors.map { error ->
            ErrorItem(
                error = error,
                selected = error.errorId in selectedErrorIds,
            )
        }
    }

    fun toggleSelection(
        item: ErrorItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.error.errorId == item.error.errorId }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedErrorIds.addOrRemove(item.error.errorId, selected)

                if (selected && userSelected && fromLongPress) {
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
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedErrorIds.add(inbetweenItem.error.errorId)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
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
            state.copy(items = newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedErrorIds.addOrRemove(it.error.errorId, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems)
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedErrorIds.addOrRemove(it.error.errorId, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }
}

@Immutable
data class DatabaseErrorScreenState(
    val isLoading: Boolean = true,
    val items: List<ErrorItem> = emptyList(),
    val messages: List<String> = emptyList(),
) {

    val selected = items.filter { it.selected }
    val selectionMode = selected.isNotEmpty()

    fun getUiModel(): List<ErrorUiModel> {
        val uiModels = mutableListOf<ErrorUiModel>()
        val errorMap = items.groupBy { (it.error as DatabaseError).errorType }
        errorMap.forEach { (errorType, errors) ->
            val message = errorType.message
            uiModels.add(ErrorUiModel.Header(message))
            uiModels.addAll(errors.map { ErrorUiModel.Item(it) })
        }
        return uiModels
    }

    fun getHeaderIndexes(): List<Int> = getUiModel()
        .withIndex()
        .filter { it.value is ErrorUiModel.Header }
        .map { it.index }
}
