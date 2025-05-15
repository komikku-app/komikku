package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.removeCovers
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.EH_PACKAGE
import exh.source.ExhPreferences
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.getMainSource
import exh.source.isEhBasedSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.SourcePagingSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.time.Instant
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

open class BrowseSourceScreenModel(
    /* KMK --> */
    protected /* KMK <-- */ val sourceId: Long,
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
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    // KMK -->
    private val toggleIncognito: ToggleIncognito = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    // KMK <--

    // SY -->
    exhPreferences: ExhPreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    // SY <--
) : StateScreenModel<BrowseSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    var source = sourceManager.getOrStub(sourceId)

    // SY -->
    val ehentaiBrowseDisplayMode by exhPreferences.enhancedEHentaiView().asState(screenModelScope)

    val startExpanded by uiPreferences.expandFilters().asState(screenModelScope)

    private val filterSerializer = FilterSerializer()
    // SY <--

    // KMK -->
    var incognitoMode = mutableStateOf(getIncognitoState.await(source.id))
    // KMK <--

    init {
        // KMK -->
        screenModelScope.launch {
            var retry = 10
            while (source !is CatalogueSource && retry-- > 0) {
                // Sometime source is late to load, so we need to wait a bit
                delay(100)
                source = sourceManager.getOrStub(sourceId)
            }
            val source = source
            if (source !is CatalogueSource) return@launch
            // KMK <--

            screenModelScope.launchIO {
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
            }.join()

            // SY -->
            val savedSearchId = savedSearch
            val jsonFilters = filtersJson
            val filters = state.value.filters
            if (savedSearchId != null) {
                val savedSearch = runBlocking { getExhSavedSearch.awaitOne(savedSearchId) { filters } }
                if (savedSearch != null) {
                    search(
                        query = savedSearch.query,
                        filters = savedSearch.filterList,
                        // KMK -->
                        savedSearchId = savedSearchId,
                        // KMK <--
                    )
                }
            } else if (jsonFilters != null) {
                runCatching {
                    val filtersJson = Json.decodeFromString<JsonArray>(jsonFilters)
                    filterSerializer.deserialize(filters, filtersJson)
                    search(filters = filters)
                }
            }

            getExhSavedSearch.subscribe(source.id, source::getFilterList)
                .map { it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name)) }
                .onEach { savedSearches ->
                    mutableState.update { it.copy(savedSearches = savedSearches.toImmutableList()) }
                }
                .launchIn(screenModelScope)
            // SY <--

            // KMK-->
            getIncognitoState.subscribe(sourceId)
                .onEach {
                    if (!it) sourcePreferences.lastUsedSource().set(source.id)
                    incognitoMode.value = it
                }
                .launchIn(screenModelScope)
            // KMK <--
        }
    }

    // KMK -->
    fun toggleIncognitoMode() {
        val packageName = when {
            source is StubSource -> null
            source.isLocal() -> LOCAL_SOURCE_PACKAGE
            source.isEhBasedSource() -> EH_PACKAGE
            else -> extensionManager.getExtensionPackage(sourceId)
        }
        packageName?.let {
            toggleIncognito.await(it, !incognitoMode.value)
        }
    }
    // KMK <--

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems().get()
    val mangaPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                // SY -->
                createSourcePagingSource(listing.query ?: "", listing.filters)
                // SY <--
            }.flow.map { pagingData ->
                pagingData.map { (manga, metadata) ->
                    getManga.subscribe(manga.url, manga.source)
                        .map { it ?: manga }
                        // SY -->
                        .combineMetadata(metadata)
                        // SY <--
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.first.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

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
    open fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        val metadataSource = source.getMainSource<MetadataSource<*, *>>()
        return flatMapLatest { manga ->
            if (metadataSource != null) {
                getFlatMetadataById.subscribe(manga.id)
                    .map { flatMetadata ->
                        manga to (flatMetadata?.raise(metadataSource.metaClass) ?: metadata)
                    }
            } else {
                flowOf(manga to null)
            }
        }
    }
    // SY <--

    fun resetFilters() {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return

        // KMK -->
        setFilters(source.getFilterList())

        reloadSavedSearches()
        // KMK <--
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: FilterList) {
        if (source !is CatalogueSource) return

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(
        query: String? = null,
        filters: FilterList? = null,
        // KMK -->
        savedSearchId: Long? = null,
        // KMK <--
    ) {
        // KMK -->
        val source = source
        // KMK <--

        if (source !is CatalogueSource) return
        // SY -->
        if (filters != null && filters !== state.value.filters) {
            // KMK -->
            setFilters(filters)
            // KMK <--
        }
        // SY <--
        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                    // KMK -->
                    savedSearchId = savedSearchId,
                    // KMK <--
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        // KMK -->
        val source = source
        // KMK <--

        if (source !is CatalogueSource) return

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
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        screenModelScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launch {
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
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    // SY -->
    open fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSource {
        return getRemoteManga(sourceId, query, filters)
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
            .orEmpty()
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): List<MangaWithChapterCount> {
        return getDuplicateLibraryManga.invoke(manga)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
            // KMK -->
            val savedSearchId: Long? = null,
            // KMK <--
        ) : Listing(query = query, filters = filters)

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

    sealed interface Dialog {
        data object Filter : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog

        // SY -->
        data class DeleteSavedSearch(val idToDelete: Long, val name: String) : Dialog
        data class CreateSavedSearch(val currentSavedSearches: ImmutableList<String>) : Dialog
        // SY <--
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        // SY -->
        val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
        val filterable: Boolean = true,
        // SY <--
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }

    // KMK -->
    private fun reloadSavedSearches() {
        screenModelScope.launchIO {
            getExhSavedSearch.await(source.id, (source as CatalogueSource)::getFilterList)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))
                .let { savedSearches ->
                    mutableState.update { it.copy(savedSearches = savedSearches.toImmutableList()) }
                }
        }
    }
    // KMK <--

    // EXH -->
    /** Show a dialog to enter name for new saved search */
    fun onSaveSearch() {
        screenModelScope.launchIO {
            val names = state.value.savedSearches.map { it.name }.toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.CreateSavedSearch(names)) }
        }
    }

    /** Open a saved search */
    fun onSavedSearch(
        // KMK -->
        loadedSearch: EXHSavedSearch,
        // KMK <--
        onToast: (StringResource) -> Unit,
    ) {
        // KMK -->
        resetFilters()
        // KMK <--
        screenModelScope.launchIO {
            // KMK -->
            val source = source
            // KMK <--
            if (source !is CatalogueSource) return@launchIO

            // KMK -->
            val search = getExhSavedSearch.awaitOne(loadedSearch.id, source::getFilterList) ?: loadedSearch
            // KMK <--

            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launchIO
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            setDialog(null)

            val filters = search.filterList
                ?.takeUnless { allDefault }
                ?: source.getFilterList()

            mutableState.update {
                it.copy(
                    listing = Listing.Search(
                        query = search.query,
                        filters = filters,
                        // KMK -->
                        savedSearchId = search.id,
                        // KMK <--
                    ),
                    filters = filters,
                    toolbarQuery = search.query,
                )
            }
        }
    }

    /** Show dialog to delete saved search */
    fun onSavedSearchPress(search: EXHSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteSavedSearch(search.id, search.name)) }
    }

    /** Save a search */
    fun saveSearch(
        name: String,
    ) {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return
        screenModelScope.launchNonCancellable {
            val query = state.value.toolbarQuery?.takeUnless {
                it.isBlank() || it == GetRemoteManga.QUERY_POPULAR || it == GetRemoteManga.QUERY_LATEST
            }?.trim()
            val filterList = state.value.filters.ifEmpty { source.getFilterList() }
            insertSavedSearch.await(
                SavedSearch(
                    id = -1,
                    source = source.id,
                    name = name.trim(),
                    query = query,
                    filtersJson = runCatching {
                        filterSerializer.serialize(filterList).ifEmpty { null }?.let { Json.encodeToString(it) }
                    }.getOrNull(),
                ),
            )
        }
    }

    fun deleteSearch(savedSearchId: Long) {
        screenModelScope.launchNonCancellable {
            deleteSavedSearchById.await(savedSearchId)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        screenModelScope.launchIO {
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }
    // EXH <--
}
