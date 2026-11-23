package app.komikku.desktop.ui.tabs

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.LibraryBatchActions
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryItemPreferences
import eu.kanade.tachiyomi.ui.library.applyLibraryFilters
import eu.kanade.tachiyomi.ui.library.applyLibrarySort
import eu.kanade.tachiyomi.ui.library.filterLibraryByQuery
import eu.kanade.tachiyomi.ui.library.groupByDefaultCategory
import eu.kanade.tachiyomi.ui.library.groupLibraryItems
import eu.kanade.tachiyomi.ui.library.hasActiveLibraryFilters
import eu.kanade.tachiyomi.ui.library.invertLibrarySelection
import eu.kanade.tachiyomi.ui.library.libraryItemPreferencesFlow
import eu.kanade.tachiyomi.ui.library.libraryTrackingFiltersFlow
import eu.kanade.tachiyomi.ui.library.selectAllLibraryItems
import eu.kanade.tachiyomi.ui.library.toLibraryItem
import eu.kanade.tachiyomi.ui.library.toggleLibraryRangeSelection
import eu.kanade.tachiyomi.ui.library.toggleLibrarySelection
import exh.log.xLogE
import exh.search.SearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
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
 * Grouping (P2): `LibraryPreferences.groupLibraryBy()` with Android's shared grouping ([groupByDefaultCategory],
 * [groupLibraryItems]: by category, source, status, tracking status, ungrouped; an active category filter with
 * included categories shows the result ungrouped, like Android). Selection and batch actions (P2): Android's selection
 * logic and action bodies from the shared `LibraryActions.kt` ([LibraryBatchActions]).
 *
 * Not on desktop: the MangaDex DMCA easter-egg query, the SY / KMK selection actions without desktop plumbing
 * (migrate, merge, recommendations, clean titles, MangaDex follow, reset info).
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
    private val context: Application = Injekt.get(),
    private val batchActions: LibraryBatchActions = LibraryBatchActions(),
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    /** Android's `lastSelectionCategory`: the page of the last (range) selection, for Shift-click ranges. */
    private var lastSelectionCategory: Long? = null

    init {
        screenModelScope.launch {
            // One preferences flow: items and filters see the same values in one pass.
            val favorites = combine(
                getLibraryManga.subscribe(),
                libraryItemPreferencesFlow(libraryPreferences, basePreferences),
                downloadCache.changes,
            ) { manga, prefs, _ ->
                prefs to manga.map { it.toLibraryItem(prefs, sourceManager, downloadManager, getMergedMangaById) }
            }
            val query = state.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
            combine(
                combine(query, getCategories.subscribe(), favorites, ::Triple),
                combine(getTracksPerManga.subscribe(), libraryTrackingFiltersFlow(trackerManager, libraryPreferences), ::Pair),
                displayPreferencesFlow(),
            ) { (query, categories, favoritesWithPrefs), (tracks, trackingFilters), display ->
                val (prefs, items) = favoritesWithPrefs
                buildResult(query, categories, prefs, items, tracks, trackingFilters, display)
            }
                .flowOn(Dispatchers.Default)
                // A failure (database, a source / tracker lookup) must not end library updates: log it, keep
                // the last good state and subscribe again.
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) return@retryWhen false
                    xLogE("Failed to load the library, retrying", cause)
                    delay((attempt + 1).coerceAtMost(5) * 1_000L)
                    true
                }
                .collectLatest { result ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            categories = result.categories,
                            pages = result.pages,
                            isLibraryEmpty = result.isLibraryEmpty,
                            hasActiveFilters = result.hasActiveFilters,
                            groupType = result.groupType,
                            itemsById = result.itemsById,
                        )
                    }
                }
        }
    }

    private suspend fun buildResult(
        query: String,
        categories: List<Category>,
        prefs: LibraryItemPreferences,
        items: List<LibraryItem>,
        tracks: Map<Long, List<Track>>,
        trackingFilters: Map<Long, TriState>,
        display: DisplayPreferences,
    ): Result {
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
        // Android's filter-icon state; the global "downloaded only" also counts here so that an empty result
        // shows "no match" (with the search / filter controls) instead of the empty-library screen.
        val hasActiveFilters = hasActiveLibraryFilters(prefs, trackingFilters) || prefs.globalFilterDownloaded
        val filteredById = filtered.associateBy { it.id }
        // Android's grouping (LibraryScreenModel.applyGrouping): an active category filter with included categories
        // shows the result ungrouped.
        val groupType = if (prefs.filterCategories && display.includedCategories.isNotEmpty()) {
            LibraryGroup.UNGROUPED
        } else {
            display.groupType
        }
        val grouped = when (groupType) {
            LibraryGroup.BY_DEFAULT -> filtered.groupByDefaultCategory(categories, display.showHiddenCategories)
            else -> filtered.groupLibraryItems(
                groupType = groupType,
                context = context,
                getTracks = getTracks,
                trackerManager = trackerManager,
                sourceManager = sourceManager,
            )
        }
            .applyLibrarySort(
                favoritesById = filteredById,
                trackMap = tracks,
                loggedInTrackerIds = trackingFilters.keys,
                groupSort = display.globalSort,
                libraryPreferences = libraryPreferences,
                trackerManager = trackerManager,
            )
        return Result(
            categories = categories,
            pages = libraryPagesOf(
                grouped = grouped,
                itemsById = filteredById,
                hideEmpty = !display.showEmptyCategoriesSearch && (query.isNotEmpty() || hasActiveFilters),
            ),
            // The whole library, before filters and search: an empty filter / search result is not an empty
            // library (no flash of the empty-library screen while a query is cleared).
            isLibraryEmpty = items.isEmpty(),
            hasActiveFilters = hasActiveFilters,
            groupType = display.groupType,
            itemsById = filteredById,
        )
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun selectCategory(categoryId: Long) {
        mutableState.update { it.copy(selectedCategoryId = categoryId) }
    }

    // Selection (Android's LibraryScreenModel, logic shared through LibraryActions.kt)

    fun clearSelection() {
        lastSelectionCategory = null
        mutableState.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelection(category: Category, mangaId: Long) {
        mutableState.update { state ->
            val newSelection = state.selection.toggleLibrarySelection(mangaId)
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            state.copy(selection = newSelection)
        }
    }

    /** Android's long press; Shift-click on desktop. */
    fun toggleRangeSelection(category: Category, mangaId: Long) {
        mutableState.update { state ->
            val newSelection = state.selection.toggleLibraryRangeSelection(
                mangaId = mangaId,
                categoryItems = state.itemIdsOf(category.id),
                isSameCategory = lastSelectionCategory == category.id,
            )
            lastSelectionCategory = category.id
            state.copy(selection = newSelection)
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        mutableState.update { state ->
            state.copy(selection = state.selection.selectAllLibraryItems(state.itemIdsOf(state.selectedPage?.category?.id)))
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        mutableState.update { state ->
            state.copy(selection = state.selection.invertLibrarySelection(state.itemIdsOf(state.selectedPage?.category?.id)))
        }
    }

    // Batch actions (Android's LibraryScreenModel, bodies shared through LibraryActions.kt)

    fun markReadSelection(read: Boolean) {
        val selection = state.value.selectedManga
        screenModelScope.launchNonCancellable { batchActions.markRead(selection, read) }
        clearSelection()
    }

    fun performDownloadAction(action: DownloadAction) {
        val mangas = state.value.selectedManga
        screenModelScope.launchNonCancellable {
            when (action) {
                DownloadAction.NEXT_1_CHAPTER -> batchActions.downloadNextChapters(mangas, 1)
                DownloadAction.NEXT_5_CHAPTERS -> batchActions.downloadNextChapters(mangas, 5)
                DownloadAction.NEXT_10_CHAPTERS -> batchActions.downloadNextChapters(mangas, 10)
                DownloadAction.NEXT_25_CHAPTERS -> batchActions.downloadNextChapters(mangas, 25)
                DownloadAction.UNREAD_CHAPTERS -> batchActions.downloadNextChapters(mangas, null)
                DownloadAction.BOOKMARKED_CHAPTERS -> batchActions.downloadBookmarkedChapters(mangas)
            }
        }
        clearSelection()
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            val mangaList = state.value.selectedManga
            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }
            val preselected = batchActions.getChangeCategorySelection(mangaList, categories)
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        screenModelScope.launchNonCancellable { batchActions.setMangaCategories(mangaList, addCategories, removeCategories) }
    }

    fun openDeleteMangaDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(it.selectedManga)) }
    }

    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable { batchActions.removeMangas(mangas, deleteFromLibrary, deleteChapters) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog
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
            groupType = group,
            includedCategories = included.mapNotNull(String::toLongOrNull).toSet(),
            excludedCategories = excluded.mapNotNull(String::toLongOrNull).toSet(),
        )
    }

    private data class DisplayPreferences(
        val showHiddenCategories: Boolean,
        val showEmptyCategoriesSearch: Boolean,
        val globalSort: LibrarySort?,
        val groupType: Int,
        val includedCategories: Set<Long>,
        val excludedCategories: Set<Long>,
    )

    private data class Result(
        val categories: List<Category>,
        val pages: List<LibraryPage>,
        val isLibraryEmpty: Boolean,
        val hasActiveFilters: Boolean,
        val groupType: Int,
        val itemsById: Map<Long, LibraryItem>,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val query: String = "",
        val selectedCategoryId: Long? = null,
        /** All categories (filter dialog). */
        val categories: List<Category> = emptyList(),
        val pages: List<LibraryPage> = emptyList(),
        val isLibraryEmpty: Boolean = true,
        val hasActiveFilters: Boolean = false,
        /** `LibraryPreferences.groupLibraryBy()` ([LibraryGroup]); pages are groups when it is not by category. */
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        /** The filtered library entries by id (Android's `LibraryData.favoritesById`). */
        val itemsById: Map<Long, LibraryItem> = emptyMap(),
        /** Selected manga ids, in selection order (Android's `selection`). */
        val selection: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        /** Index of the selected page (the first one when the selected category is not shown). */
        val selectedIndex: Int
            get() = pages.indexOfFirst { it.category.id == selectedCategoryId }.coerceAtLeast(0)

        val selectedPage: LibraryPage?
            get() = pages.getOrNull(selectedIndex)

        val selectionMode: Boolean
            get() = selection.isNotEmpty()

        /** The selected entries that are still in the (filtered) library, like Android. */
        val selectedManga: List<Manga>
            get() = selection.mapNotNull { itemsById[it]?.libraryManga?.manga }

        fun itemIdsOf(categoryId: Long?): List<Long> =
            pages.firstOrNull { it.category.id == categoryId }?.items?.map { it.id }.orEmpty()
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
