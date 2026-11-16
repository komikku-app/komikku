package app.komikku.desktop.ui.tabs

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop library state: the user's categories as pages, each with its library manga, read through
 * the shared interactors. A first, read-only cut of the Android `LibraryScreenModel` (no filters,
 * sorting options, search or selection yet; Phase 6.3).
 */
class LibraryScreenModel(
    getCategories: GetCategories = Injekt.get(),
    getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateScreenModel<LibraryScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch {
            combine(getCategories.subscribe(), getLibraryManga.subscribe(), ::libraryPages)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    logcat(LogPriority.ERROR, e) { "Failed to load the library" }
                    emit(emptyList())
                }
                .collectLatest { pages -> mutableState.update { State.Success(pages) } }
        }
    }

    sealed interface State {
        data object Loading : State

        @Immutable
        data class Success(val pages: List<LibraryPage>) : State {
            val isEmpty: Boolean get() = pages.all { it.manga.isEmpty() }
        }
    }
}

@Immutable
data class LibraryPage(
    val category: Category,
    val manga: List<LibraryManga>,
)

/**
 * Groups [libraryManga] by category: visible categories in their user order, each with its manga
 * sorted by title. Manga without a category belong to the default (system) category, which is only
 * shown when it has manga or when there is no other category (like on Android).
 */
fun libraryPages(categories: List<Category>, libraryManga: List<LibraryManga>): List<LibraryPage> {
    val mangaByCategory = libraryManga
        .flatMap { manga -> manga.categories.ifEmpty { listOf(Category.UNCATEGORIZED_ID) }.map { it to manga } }
        .groupBy({ it.first }, { it.second })
    val visible = categories.filterNot { it.hidden }.sortedBy { it.order }
    return visible
        .filter { category ->
            !category.isSystemCategory ||
                mangaByCategory[category.id].orEmpty().isNotEmpty() ||
                visible.none { !it.isSystemCategory }
        }
        .map { category ->
            LibraryPage(
                category = category,
                manga = mangaByCategory[category.id].orEmpty()
                    .distinctBy { it.manga.id }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.manga.title }),
            )
        }
}
