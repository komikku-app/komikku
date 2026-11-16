package app.komikku.desktop.ui.tabs

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
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
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop library state: the user's categories as pages, each with its library manga, read through
 * the shared interactors. A first, read-only cut of the Android `LibraryScreenModel` (only the global
 * "downloaded only" filter; no per-library filters, sorting options, search or selection yet; Phase 6.3).
 */
class LibraryScreenModel(
    getCategories: GetCategories = Injekt.get(),
    getLibraryManga: GetLibraryManga = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    downloadManager: DownloadManager = Injekt.get(),
    downloadCache: DownloadCache = Injekt.get(),
) : StateScreenModel<LibraryScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch {
            // "Downloaded only" (More tab) keeps local manga and manga with downloaded chapters, like Android.
            val library = combine(
                getLibraryManga.subscribe(),
                basePreferences.downloadedOnly().changes(),
                downloadCache.changes,
            ) { manga, downloadedOnly, _ ->
                if (!downloadedOnly) {
                    manga
                } else {
                    manga.filter { it.manga.isLocal() || downloadManager.getDownloadCount(it.manga) > 0 }
                }
            }
            combine(getCategories.subscribe(), library, ::libraryPages)
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

/**
 * Library search (8.5, Android `LibraryScreenModel` text search subset): keeps manga whose title, author,
 * artist or one of the genres contains [query] (case-insensitive); blank = everything. Pages stay (empty
 * pages show the "empty category" message).
 */
fun List<LibraryPage>.search(query: String): List<LibraryPage> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return map { page ->
        page.copy(
            manga = page.manga.filter { item ->
                val manga = item.manga
                manga.title.contains(q, ignoreCase = true) ||
                    manga.author?.contains(q, ignoreCase = true) == true ||
                    manga.artist?.contains(q, ignoreCase = true) == true ||
                    manga.genre.orEmpty().any { it.equals(q, ignoreCase = true) }
            },
        )
    }
}
