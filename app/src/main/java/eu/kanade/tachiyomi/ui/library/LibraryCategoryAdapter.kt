package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import eu.kanade.domain.manga.interactor.GetSearchTags
import eu.kanade.domain.manga.interactor.GetSearchTitles
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.lang.withUIContext
import exh.log.xLogW
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.source.isMetadataSource
import exh.util.cancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(view: LibraryCategoryView, val controller: LibraryController) :
    FlexibleAdapter<LibraryItem>(null, view, true) {
    // EXH -->
    private val searchEngine = SearchEngine()
    private var lastFilterJob: Job? = null
    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()
    private val hasLoggedServices by lazy {
        trackManager.hasLoggedServices()
    }
    private val services = trackManager.services.associate { service ->
        service.id to controller.activity!!.getString(service.nameRes())
    }
    private val getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata by injectLazy()
    private val getTracks: GetTracks by injectLazy()
    private val getSearchTags: GetSearchTags by injectLazy()
    private val getSearchTitles: GetSearchTitles by injectLazy()

    // Keep compatibility as searchText field was replaced when we upgraded FlexibleAdapter
    var searchText
        get() = getFilter(String::class.java).orEmpty()
        set(value) {
            setFilter(value)
        }
    // EXH <--

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    suspend fun setItems(scope: CoroutineScope, list: List<LibraryItem>) {
        // A copy of manga always unfiltered.
        mangas = list.toList()

        performFilter(scope)
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return currentItems.indexOfFirst { it.manga.id == manga.id }
    }

    fun canDrag() = (mode != Mode.MULTI || (mode == Mode.MULTI && selectedItemCount == 1)) &&
        searchText.isBlank() &&
        preferences.groupLibraryBy().get() == LibraryGroup.BY_DEFAULT &&
        !preferences.downloadedOnly().get() &&
        preferences.filterDownloaded().get() == Filter.TriState.STATE_IGNORE &&
        preferences.filterCompleted().get() == Filter.TriState.STATE_IGNORE &&
        preferences.filterStarted().get() == Filter.TriState.STATE_IGNORE &&
        preferences.filterUnread().get() == Filter.TriState.STATE_IGNORE &&
        services.all { preferences.filterTracking(it.key).get() == Filter.TriState.STATE_IGNORE } &&
        preferences.filterLewd().get() == Filter.TriState.STATE_IGNORE

    // EXH -->
    // Note that we cannot use FlexibleAdapter's built in filtering system as we cannot cancel it
    //   (well technically we can cancel it by invoking filterItems again but that doesn't work when
    //    we want to perform a no-op filter)
    suspend fun performFilter(scope: CoroutineScope) {
        isLongPressDragEnabled = canDrag()
        lastFilterJob?.cancel()
        if (mangas.isNotEmpty() && searchText.isNotBlank()) {
            val savedSearchText = searchText

            val job = scope.launch(Dispatchers.IO) {
                val newManga = try {
                    // Prepare filter object
                    val parsedQuery = searchEngine.parseQuery(savedSearchText)
                    val mangaWithMetaIds = getIdsOfFavoriteMangaWithMetadata.await()

                    ensureActive() // Fail early when cancelled

                    // Flow the mangas to allow cancellation of this filter operation
                    mangas.asFlow().cancellable().filter { item ->
                        if (isMetadataSource(item.manga.source)) {
                            val mangaId = item.manga.id ?: -1
                            if (mangaWithMetaIds.binarySearch(mangaId) < 0) {
                                // No meta? Filter using title
                                filterManga(parsedQuery, item.manga)
                            } else {
                                val tags = getSearchTags.await(mangaId)
                                val titles = getSearchTitles.await(mangaId)
                                filterManga(parsedQuery, item.manga, false, tags, titles)
                            }
                        } else {
                            filterManga(parsedQuery, item.manga)
                        }
                    }.toList()
                } catch (e: Exception) {
                    // Do not catch cancellations
                    if (e is CancellationException) throw e

                    this@LibraryCategoryAdapter.xLogW("Could not filter mangas!", e)
                    mangas
                }

                withUIContext {
                    updateDataSet(newManga)
                }
            }
            lastFilterJob = job
            job.join()
        } else {
            updateDataSet(mangas)
        }
    }

    private suspend fun filterManga(
        queries: List<QueryComponent>,
        manga: LibraryManga,
        checkGenre: Boolean = true,
        searchTags: List<SearchTag>? = null,
        searchTitles: List<SearchTitle>? = null,
    ): Boolean {
        val mappedQueries = queries.groupBy { it.excluded }
        val tracks = if (hasLoggedServices) getTracks.await(manga.id!!).toList() else null
        val source = sourceManager.get(manga.source)
        val genre = if (checkGenre) manga.getGenres().orEmpty() else emptyList()
        val hasNormalQuery = mappedQueries[false]?.all { queryComponent ->
            when (queryComponent) {
                is Text -> {
                    val query = queryComponent.asQuery()
                    manga.title.contains(query, true) ||
                        (manga.author?.contains(query, true) == true) ||
                        (manga.artist?.contains(query, true) == true) ||
                        (manga.description?.contains(query, true) == true) ||
                        (source?.name?.contains(query, true) == true) ||
                        (hasLoggedServices && tracks != null && filterTracks(query, tracks)) ||
                        (genre.any { it.contains(query, true) }) ||
                        (searchTags.orEmpty().any { it.name.contains(query, true) }) ||
                        (searchTitles.orEmpty().any { it.title.contains(query, true) })
                }
                is Namespace -> {
                    searchTags != null && searchTags.any {
                        val tag = queryComponent.tag
                        (it.namespace != null && it.namespace.contains(queryComponent.namespace, true) && tag != null && it.name.contains(tag.asQuery(), true)) ||
                            (tag == null && it.namespace != null && it.namespace.contains(queryComponent.namespace, true))
                    }
                }
                else -> true
            }
        }
        val doesNotHaveExcludedQuery = mappedQueries[true]?.all { queryComponent ->
            when (queryComponent) {
                is Text -> {
                    val query = queryComponent.asQuery()
                    query.isBlank() || (
                        (!manga.title.contains(query, true)) &&
                            (!manga.author.orEmpty().contains(query, true)) &&
                            (!manga.artist.orEmpty().contains(query, true)) &&
                            (!manga.description.orEmpty().contains(query, true)) &&
                            (!source?.name.orEmpty().contains(query, true)) &&
                            (!hasLoggedServices || hasLoggedServices && tracks == null || tracks != null && !filterTracks(query, tracks)) &&
                            (genre.none { it.contains(query, true) }) &&
                            (searchTags.orEmpty().none { it.name.contains(query, true) }) &&
                            (searchTitles.orEmpty().none { it.title.contains(query, true) })
                        )
                }
                is Namespace -> {
                    val searchedTag = queryComponent.tag?.asQuery()
                    searchTags == null || searchTags.all { mangaTag ->
                        if (searchedTag == null || searchedTag.isBlank()) {
                            mangaTag.namespace == null || !mangaTag.namespace.contains(queryComponent.namespace, true)
                        } else if (mangaTag.namespace == null) {
                            true
                        } else {
                            !(mangaTag.name.contains(searchedTag, true) && mangaTag.namespace.contains(queryComponent.namespace, true))
                        }
                    }
                }
                else -> true
            }
        }

        return (hasNormalQuery != null && doesNotHaveExcludedQuery != null && hasNormalQuery && doesNotHaveExcludedQuery) ||
            (hasNormalQuery != null && doesNotHaveExcludedQuery == null && hasNormalQuery) ||
            (hasNormalQuery == null && doesNotHaveExcludedQuery != null && doesNotHaveExcludedQuery)
    }

    private fun filterTracks(constraint: String, tracks: List<eu.kanade.domain.track.model.Track>): Boolean {
        return tracks.any {
            val trackService = trackManager.getService(it.syncId)
            if (trackService != null) {
                val status = trackService.getStatus(it.status.toInt())
                val name = services[it.syncId]
                return@any status.contains(constraint, true) || name?.contains(constraint, true) == true
            }
            return@any false
        }
    }
    // EXH <--
}
