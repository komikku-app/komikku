package eu.kanade.presentation.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryGroup
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
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
    var dialog: LibraryPresenter.Dialog?

    // SY -->
    val ogCategories: List<Category>
    val groupType: Int
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
    override var dialog: LibraryPresenter.Dialog? by mutableStateOf(null)

    // SY -->
    override var groupType: Int by mutableStateOf(LibraryGroup.BY_DEFAULT)

    override var ogCategories: List<Category> by mutableStateOf(emptyList())

    override var showSyncExh: Boolean by mutableStateOf(true)
    override val showCleanTitles: Boolean by derivedStateOf {
        selection.any {
            it.manga.isEhBasedManga() ||
                it.manga.source in nHentaiSourceIds
        }
    }
    override val showAddToMangadex: Boolean by derivedStateOf {
        selection.any { it.manga.source in mangaDexSourceIds }
    }
    // SY <--
}
