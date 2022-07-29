package eu.kanade.presentation.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds

@Stable
interface LibraryState {
    val isLoading: Boolean
    val categories: List<Category>
    var searchQuery: String?
    val selection: List<LibraryManga>
    val selectionMode: Boolean
    var hasActiveFilters: Boolean

    // SY -->
    val ogCategories: List<Category>
    val showSyncExh: Boolean
    val showCleanTitles: Boolean
    val showAddToMangadex: Boolean
    // SY <--
}

fun LibraryState(): LibraryState {
    return LibraryStateImpl()
}

class LibraryStateImpl : LibraryState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var categories: List<Category> by mutableStateOf(emptyList())
    override var searchQuery: String? by mutableStateOf(null)
    override var selection: List<LibraryManga> by mutableStateOf(emptyList())
    override val selectionMode: Boolean by derivedStateOf { selection.isNotEmpty() }
    override var hasActiveFilters: Boolean by mutableStateOf(false)

    // SY -->
    override var ogCategories: List<Category> by mutableStateOf(emptyList())

    override var showSyncExh: Boolean by mutableStateOf(true)
    override val showCleanTitles: Boolean by derivedStateOf {
        selection.any {
            it.isEhBasedManga() ||
                it.source in nHentaiSourceIds ||
                it.source == PERV_EDEN_EN_SOURCE_ID ||
                it.source == PERV_EDEN_IT_SOURCE_ID
        }
    }
    override val showAddToMangadex: Boolean by derivedStateOf {
        selection.any { it.source in mangaDexSourceIds }
    }
    // SY <--
}
