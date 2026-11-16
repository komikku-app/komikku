package app.komikku.desktop.ui.tabs

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryItemPreferences
import eu.kanade.tachiyomi.ui.library.applyLibraryFilters
import eu.kanade.tachiyomi.ui.library.applyLibrarySort
import eu.kanade.tachiyomi.ui.library.filterLibraryByQuery
import eu.kanade.tachiyomi.ui.library.libraryItemPreferencesFlow
import eu.kanade.tachiyomi.ui.library.libraryTrackingFiltersFlow
import eu.kanade.tachiyomi.ui.library.toLibraryItem
import exh.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetSearchTags
import tachiyomi.domain.manga.interactor.GetSearchTitles
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop library state, built like Android's `LibraryScreenModel` from the same shared code
 * (`LibraryFilterSort.kt`, borrowed): library items with their badges ([toLibraryItem]), the library filters
 * ([applyLibraryFilters]: downloaded / unread / started / bookmarked / completed / custom interval / lewd /
 * trackers / categories, global "downloaded only"), the SY text search ([filterLibraryByQuery], debounced,
 * off the UI thread) and the per-category sort ([applyLibrarySort]; the global sort when the Android
 * grouping preference is not "by category"). Pages are the visible categories; when a search or filter is
 * active, empty categories are hidden unless `showEmptyCategoriesSearch` is on (as on Android). The query and
 * the selected category live here, so they survive the pages leaving composition.
 *
 * Not on desktop: grouping by source / status / tracker / ungrouped (pages are always categories),
 * selection and its actions, the MangaDex DMCA easter-egg query.
 */
class LibraryScreenModel(
    private val getCategories: GetCategories = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val searchEngine: SearchEngine = Injekt.get(),
    private val getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getSearchTags: GetSearchTags = Injekt.get(),
    private val getSearchTitles: GetSearchTitles = Injekt.get(),
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val itemPreferences = libraryItemPreferencesFlow(libraryPreferences, basePreferences)
            val favorites = combine(getLibraryManga.subscribe(), itemPreferences, downloadCache.changes) { manga, prefs, _ ->
                manga.map { it.toLibraryItem(prefs, sourceManager, downloadManager, getMergedMangaById) }
            }
            val query = state.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
            combine(
                combine(query, getCategories.subscribe(), favorites, ::Triple),
                combine(getTracksPerManga.subscribe(), libraryTrackingFiltersFlow(trackerManager, libraryPreferences), ::Pair),
                itemPreferences,
                displayPreferencesFlow(),
            ) { (query, categories, items), (tracks, trackingFilters), prefs, display ->
                val filtered = items
                    .applyLibraryFilters(
                        trackMap = tracks,
                        trackingFilter = trackingFilters,
                        preferences = prefs,
                        includedCategories = display.includedCategories,
                        excludedCategories = display.excludedCategories,
                        downloadManager = downloadManager,
                        getMergedMangaById = getMergedMangaById,
                    )
                    .let {
                        filterLibraryByQuery(
                            unfiltered = it,
                            query = query.ifEmpty { null },
                            loggedInTrackServices = trackingFilters,
                            searchEngine = searchEngine,
                            getIdsOfFavoriteMangaWithMetadata = getIdsOfFavoriteMangaWithMetadata,
                            getTracks = getTracks,
                            sourceManager = sourceManager,
                            getSearchTags = getSearchTags,
                            getSearchTitles = getSearchTitles,
                            trackerManager = trackerManager,
                        )
                    }
                val hasActiveFilters = prefs.hasActiveFilters(trackingFilters)
                val grouped = groupByCategory(categories, filtered, display.showHiddenCategories)
                    .applyLibrarySort(
                        favoritesById = filtered.associateBy { it.id },
                        trackMap = tracks,
                        loggedInTrackerIds = trackingFilters.keys,
                        groupSort = display.globalSort,
                        libraryPreferences = libraryPreferences,
                        trackerManager = trackerManager,
                    )
                Result(
                    pages = libraryPagesOf(
                        grouped = grouped,
                        itemsById = filtered.associateBy { it.id },
                        hideEmpty = !display.showEmptyCategoriesSearch && (query.isNotEmpty() || hasActiveFilters),
                    ),
                    isLibraryEmpty = filtered.isEmpty(),
                    hasActiveFilters = hasActiveFilters,
                )
            }
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logcat(LogPriority.ERROR, e) { "Failed to load the library" }
                    emit(Result(emptyList(), isLibraryEmpty = true, hasActiveFilters = false))
                }
                .collectLatest { result ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            pages = result.pages,
                            isLibraryEmpty = result.isLibraryEmpty,
                            hasActiveFilters = result.hasActiveFilters,
                        )
                    }
                }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun selectCategory(categoryId: Long) {
        mutableState.update { it.copy(selectedCategoryId = categoryId) }
    }

    private fun displayPreferencesFlow(): Flow<DisplayPreferences> = combine(
        combine(
            libraryPreferences.showHiddenCategories().changes(),
            libraryPreferences.showEmptyCategoriesSearch().changes(),
            libraryPreferences.randomSortSeed().changes(),
            ::Triple,
        ),
        combine(libraryPreferences.groupLibraryBy().changes(), libraryPreferences.sortingMode().changes(), ::Pair),
        combine(
            libraryPreferences.filterCategoriesInclude().changes(),
            libraryPreferences.filterCategoriesExclude().changes(),
            ::Pair,
        ),
    ) { (showHidden, showEmpty, _), (group, sort), (included, excluded) ->
        DisplayPreferences(
            showHiddenCategories = showHidden,
            showEmptyCategoriesSearch = showEmpty,
            // Android: the global sort applies when the library is not grouped by category.
            globalSort = sort.takeIf { group != LibraryGroup.BY_DEFAULT },
            includedCategories = included.mapNotNull(String::toLongOrNull).toSet(),
            excludedCategories = excluded.mapNotNull(String::toLongOrNull).toSet(),
        )
    }

    private data class DisplayPreferences(
        val showHiddenCategories: Boolean,
        val showEmptyCategoriesSearch: Boolean,
        val globalSort: LibrarySort?,
        val includedCategories: Set<Long>,
        val excludedCategories: Set<Long>,
    )

    private data class Result(val pages: List<LibraryPage>, val isLibraryEmpty: Boolean, val hasActiveFilters: Boolean)

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val query: String = "",
        val selectedCategoryId: Long? = null,
        val pages: List<LibraryPage> = emptyList(),
        val isLibraryEmpty: Boolean = true,
        val hasActiveFilters: Boolean = false,
    ) {
        /** Index of the selected page (the first one when the selected category is not shown). */
        val selectedIndex: Int
            get() = pages.indexOfFirst { it.category.id == selectedCategoryId }.coerceAtLeast(0)
    }

    companion object {
        /** Same as Android's `SEARCH_DEBOUNCE_MILLIS`. */
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}

@Immutable
data class LibraryPage(
    val category: Category,
    val items: List<LibraryItem>,
) {
    val manga: List<LibraryManga> get() = items.map { it.libraryManga }
}

/** Android's `hasActiveFilters` (the library filter icon is highlighted). */
fun LibraryItemPreferences.hasActiveFilters(trackingFilters: Map<Long, TriState>): Boolean =
    listOf(
        filterDownloaded,
        filterUnread,
        filterStarted,
        filterBookmarked,
        filterCompleted,
        filterIntervalCustom,
        filterLewd,
        *trackingFilters.values.toTypedArray(),
    ).any { it != TriState.DISABLED } || filterCategories

/**
 * Groups [items] by category like Android's "by category" grouping: categories in user order, hidden ones
 * only with [showHiddenCategories]; the default (system) category only when it has items or is the only
 * category. Items keep their order (sorting is done by [applyLibrarySort]).
 */
fun groupByCategory(
    categories: List<Category>,
    items: List<LibraryItem>,
    showHiddenCategories: Boolean,
): Map<Category, List<Long>> {
    val idsByCategory = items
        .flatMap { item -> item.libraryManga.categories.ifEmpty { listOf(Category.UNCATEGORIZED_ID) }.map { it to item.id } }
        .groupBy({ it.first }, { it.second })
    val visible = categories.filter { showHiddenCategories || !it.hidden }.sortedBy { it.order }
    return visible
        .filter { category ->
            !category.isSystemCategory ||
                idsByCategory[category.id].orEmpty().isNotEmpty() ||
                visible.none { !it.isSystemCategory }
        }
        .associateWith { idsByCategory[it.id].orEmpty().distinct() }
}

private fun libraryPagesOf(
    grouped: Map<Category, List<Long>>,
    itemsById: Map<Long, LibraryItem>,
    hideEmpty: Boolean,
): List<LibraryPage> {
    val pages = grouped
        .map { (category, ids) -> LibraryPage(category, ids.mapNotNull { itemsById[it] }) }
        .filter { !hideEmpty || it.items.isNotEmpty() }
    // Android falls back to the default category when nothing is left.
    return pages.ifEmpty { listOf(LibraryPage(Category(Category.UNCATEGORIZED_ID, "", 0, 0, false), emptyList())) }
}
