package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.navigator.Navigator
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.asState
import eu.kanade.core.prefs.mapAsCheckboxState
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetMangaDefaultChapterFlags
import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetDuplicateLibraryManga
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.interactor.DeleteSavedSearchById
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.domain.source.interactor.InsertSavedSearch
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.browse.source.filter.AutoComplete
import eu.kanade.tachiyomi.ui.browse.source.filter.AutoCompleteSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxItem
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.browse.source.filter.HeaderItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SeparatorItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.browse.source.filter.SortItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.savedsearches.EXHSavedSearch
import exh.savedsearches.models.SavedSearch
import exh.source.getMainSource
import exh.util.nullIfBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.Date
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

open class BrowseSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    // SY -->
    private val filtersJson: String? = null,
    private val savedSearch: Long? = null,
    // SY <--
    private val sourceManager: SourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getRemoteManga: GetRemoteManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val syncChaptersWithTrackServiceTwoWay: SyncChaptersWithTrackServiceTwoWay = Injekt.get(),

    // SY -->
    unsortedPreferences: UnsortedPreferences = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    // SY <--
) : StateScreenModel<BrowseSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }

    var displayMode by sourcePreferences.sourceDisplayMode().asState(coroutineScope)

    val source = sourceManager.get(sourceId) as CatalogueSource

    // SY -->
    val ehentaiBrowseDisplayMode by unsortedPreferences.enhancedEHentaiView().asState(coroutineScope)

    private val filterSerializer = FilterSerializer()
    // SY <--

    init {
        mutableState.update {
            var query: String? = null
            var listing = it.listing

            if (listing is Listing.Search) {
                query = listing.query
                listing = Listing.Search(query, source.getFilterList())
            }

            it.copy(
                listing = listing,
                filters = source.getFilterList(),
                toolbarQuery = query,
            )
        }

        // SY -->
        val savedSearchFilters = savedSearch
        val jsonFilters = filtersJson
        val filters = state.value.filters
        if (savedSearchFilters != null) {
            val savedSearch = runBlocking { getExhSavedSearch.awaitOne(savedSearchFilters) { filters } }
            if (savedSearch != null) {
                search(query = savedSearch.query.nullIfBlank(), filters = savedSearch.filterList)
            }
        } else if (jsonFilters != null) {
            runCatching {
                val filtersJson = Json.decodeFromString<JsonArray>(jsonFilters)
                filterSerializer.deserialize(filters, filtersJson)
                search(filters = filters)
            }
        }

        getExhSavedSearch.subscribe(source.id, source::getFilterList)
            .onEach { savedSearches ->
                mutableState.update { it.copy(savedSearches = savedSearches) }
                withUIContext {
                    filterSheet?.setSavedSearches(savedSearches)
                }
            }
            .launchIn(coroutineScope)
        // SY <--
    }

    /**
     * Sheet containing filter items.
     */
    private var filterSheet: SourceFilterSheet? = null

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    val mangaPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(
                PagingConfig(pageSize = 25),
            ) {
                // SY -->
                createSourcePagingSource(listing.query ?: "", listing.filters)
                // SY <--
            }.flow
                .map { pagingData ->
                    pagingData.map { (sManga, metadata) ->
                        val dbManga = withIOContext { networkToLocalManga.await(sManga.toDomainManga(sourceId)) }
                        getManga.subscribe(dbManga.url, dbManga.source)
                            .filterNotNull()
                            .onEach { initializeManga(it) }
                            // SY -->
                            .combineMetadata(dbManga, metadata)
                            // SY <--
                            .stateIn(coroutineScope)
                    }
                }
                .cachedIn(coroutineScope)
        }
        .stateIn(coroutineScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns()
        } else {
            libraryPreferences.portraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    // SY -->
    open fun Flow<Manga>.combineMetadata(dbManga: Manga, metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        val metadataSource = source.getMainSource<MetadataSource<*, *>>()
        return combine(getFlatMetadataById.subscribe(dbManga.id)) { manga, flatMetadata ->
            metadataSource ?: return@combine manga to metadata
            manga to (flatMetadata?.raise(metadataSource.metaClass) ?: metadata)
        }
    }
    // SY <--

    fun resetFilters() {
        mutableState.update { it.copy(filters = source.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing) }
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        // SY -->
        if (filters != null && filters !== state.value.filters) {
            mutableState.update { state -> state.copy(filters = filters) }
        }
        // SY <--
        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        val defaultFilters = source.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is SourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is SourceModelFilter.TriState -> filter.state = 1
                            is SourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        mutableState.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    /**
     * Initialize a manga.
     *
     * @param manga to initialize.
     */
    private suspend fun initializeManga(manga: Manga) {
        if (manga.thumbnailUrl != null || manga.initialized) return
        withNonCancellableContext {
            try {
                val networkManga = source.getMangaDetails(manga.toSManga())
                val updatedManga = manga.copyFrom(networkManga)
                    .copy(initialized = true)

                updateManga.await(updatedManga.toMangaUpdate())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        coroutineScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Date().time
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)

                autoAddTrack(manga)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun getSourceOrStub(manga: Manga): Source {
        return sourceManager.getOrStub(manga.source)
    }

    fun addFavorite(manga: Manga) {
        coroutineScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)

                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)

                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(Dialog.ChangeMangaCategory(manga, categories.mapAsCheckboxState { it.id in preselectedIds }))
                }
            }
        }
    }

    private suspend fun autoAddTrack(manga: Manga) {
        loggedServices
            .filterIsInstance<EnhancedTrackService>()
            .filter { it.accept(source) }
            .forEach { service ->
                try {
                    service.match(manga)?.let { track ->
                        track.manga_id = manga.id
                        (service as TrackService).bind(track)
                        insertTrack.await(track.toDomainTrack()!!)

                        val chapters = getChapterByMangaId.await(manga.id)
                        syncChaptersWithTrackServiceTwoWay.await(chapters, track.toDomainTrack()!!, service)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not match manga: ${manga.title} with service $service" }
                }
            }
    }

    // SY -->
    open fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return getRemoteManga.subscribe(sourceId, query, filters)
    }
    // SY <--

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            ?: emptyList()
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): Manga? {
        return getDuplicateLibraryManga.await(manga.title, manga.source)
    }

    fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        coroutineScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        filterSheet?.show()
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    open fun initFilterSheet(context: Context, navigator: Navigator) {
        val state = state.value
        /*if (state.filters.isEmpty()) {
            return
        }*/

        filterSheet = SourceFilterSheet(
            context = context,
            // SY -->
            navigator = navigator,
            source = source,
            searches = state.savedSearches,
            // SY <--
            onFilterClicked = { search(filters = state.filters) },
            onResetClicked = {
                resetFilters()
                filterSheet?.setFilters(state.filterItems)
            },
            // EXH -->
            onSaveClicked = {
                coroutineScope.launchIO {
                    val names = loadSearches().map { it.name }
                    mutableState.update { it.copy(dialog = Dialog.CreateSavedSearch(names)) }
                }
            },
            onSavedSearchClicked = { idOfSearch ->
                coroutineScope.launchIO {
                    val search = loadSearch(idOfSearch)

                    if (search == null) {
                        mutableState.update { it.copy(dialog = Dialog.FailedToLoadSavedSearch) }
                        return@launchIO
                    }

                    if (search.filterList == null && state.filters.isNotEmpty()) {
                        withUIContext {
                            context.toast(R.string.save_search_invalid)
                        }
                        return@launchIO
                    }

                    val allDefault = search.filterList != null && state.filters == source.getFilterList()
                    filterSheet?.dismiss()

                    search(
                        query = search.query,
                        filters = if (allDefault) null else search.filterList,
                    )
                }
            },
            onSavedSearchDeleteClicked = { idToDelete, name ->
                mutableState.update { it.copy(dialog = Dialog.DeleteSavedSearch(idToDelete, name)) }
            },
            // EXH <--
        )

        filterSheet?.setFilters(state.filterItems)
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(override val query: String?, override val filters: FilterList) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed class Dialog {
        data class RemoveManga(val manga: Manga) : Dialog()
        data class AddDuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog()
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState.State<Category>>,
        ) : Dialog()
        data class Migrate(val newManga: Manga) : Dialog()

        // SY -->
        object FailedToLoadSavedSearch : Dialog()
        data class DeleteSavedSearch(val idToDelete: Long, val name: String) : Dialog()
        data class CreateSavedSearch(val currentSavedSearches: List<String>) : Dialog()
        // SY <--
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        // SY -->
        val savedSearches: List<EXHSavedSearch> = emptyList(),
        // SY <--
    ) {
        val filterItems get() = filters.toItems()
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }

    // EXH -->
    fun saveSearch(
        name: String,
    ) {
        coroutineScope.launchNonCancellable {
            val query = state.value.listing.query
            val filterList = state.value.listing.filters.ifEmpty { source.getFilterList() }
            insertSavedSearch.await(
                SavedSearch(
                    id = -1,
                    source = source.id,
                    name = name.trim(),
                    query = query?.nullIfBlank(),
                    filtersJson = runCatching { filterSerializer.serialize(filterList).ifEmpty { null }?.let { Json.encodeToString(it) } }.getOrNull(),
                ),
            )
        }
    }

    fun deleteSearch(savedSearchId: Long) {
        coroutineScope.launchNonCancellable {
            deleteSavedSearchById.await(savedSearchId)
        }
    }

    suspend fun loadSearch(searchId: Long) =
        getExhSavedSearch.awaitOne(searchId, source::getFilterList)

    suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, source::getFilterList)
    // EXH <--
}

fun FilterList.toItems(): List<IFlexible<*>> {
    return mapNotNull { filter ->
        when (filter) {
            // --> EXH
            is SourceModelFilter.AutoComplete -> AutoComplete(filter)
            // <-- EXH
            is SourceModelFilter.Header -> HeaderItem(filter)
            is SourceModelFilter.Separator -> SeparatorItem(filter)
            is SourceModelFilter.CheckBox -> CheckboxItem(filter)
            is SourceModelFilter.TriState -> TriStateItem(filter)
            is SourceModelFilter.Text -> TextItem(filter)
            is SourceModelFilter.Select<*> -> SelectItem(filter)
            is SourceModelFilter.Group<*> -> {
                val group = GroupItem(filter)
                val subItems = filter.state.mapNotNull {
                    when (it) {
                        is SourceModelFilter.CheckBox -> CheckboxSectionItem(it)
                        is SourceModelFilter.TriState -> TriStateSectionItem(it)
                        is SourceModelFilter.Text -> TextSectionItem(it)
                        is SourceModelFilter.Select<*> -> SelectSectionItem(it)
                        // SY -->
                        is SourceModelFilter.AutoComplete -> AutoCompleteSectionItem(it)
                        // SY <--
                        else -> null
                    }
                }
                subItems.forEach { it.header = group }
                group.subItems = subItems
                group
            }
            is SourceModelFilter.Sort -> {
                val group = SortGroup(filter)
                val subItems = filter.values.map {
                    SortItem(it, group)
                }
                group.subItems = subItems
                group
            }
        }
    }
}
