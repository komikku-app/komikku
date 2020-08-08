package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.category.CategoryAdapter
import exh.isLewdSource
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.util.await
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(view: LibraryCategoryView, val controller: LibraryController) :
    FlexibleAdapter<LibraryItem>(null, view, true) {
    // EXH -->
    private val db: DatabaseHelper by injectLazy()
    private val searchEngine = SearchEngine()
    private var lastFilterJob: Job? = null
    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val hasLoggedServices by lazy {
        trackManager.hasLoggedServices()
    }

    // Keep compatibility as searchText field was replaced when we upgraded FlexibleAdapter
    var searchText
        get() = getFilter(String::class.java) ?: ""
        set(value) {
            setFilter(value)
        }
    // EXH <--

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    // SY -->
    val onItemReleaseListener: CategoryAdapter.OnItemReleaseListener = view
    // SY <--

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

    fun canDrag() = (mode != Mode.MULTI || (mode == Mode.MULTI && selectedItemCount == 1)) && searchText.isBlank()

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

                    val mangaWithMetaIdsQuery = db.getIdsOfFavoriteMangaWithMetadata().await()
                    val mangaWithMetaIds = LongArray(mangaWithMetaIdsQuery.count)
                    if (mangaWithMetaIds.isNotEmpty()) {
                        val mangaIdCol = mangaWithMetaIdsQuery.getColumnIndex(MangaTable.COL_ID)
                        mangaWithMetaIdsQuery.moveToFirst()
                        while (!mangaWithMetaIdsQuery.isAfterLast) {
                            ensureActive() // Fail early when cancelled

                            mangaWithMetaIds[mangaWithMetaIdsQuery.position] = mangaWithMetaIdsQuery.getLong(mangaIdCol)
                            mangaWithMetaIdsQuery.moveToNext()
                        }
                    }

                    ensureActive() // Fail early when cancelled

                    // Flow the mangas to allow cancellation of this filter operation
                    mangas.asFlow().cancellable().filter { item ->
                        if (isLewdSource(item.manga.source)) {
                            val mangaId = item.manga.id ?: -1
                            if (mangaWithMetaIds.binarySearch(mangaId) < 0) {
                                // No meta? Filter using title
                                filterManga(parsedQuery, item.manga)
                            } else {
                                val tags = db.getSearchTagsForManga(mangaId).await()
                                val titles = db.getSearchTitlesForManga(mangaId).await()
                                filterManga(parsedQuery, item.manga, false, tags, titles)
                            }
                        } else {
                            filterManga(parsedQuery, item.manga)
                        }
                    }.toList()
                } catch (e: Exception) {
                    // Do not catch cancellations
                    if (e is CancellationException) throw e

                    Timber.w(e, "Could not filter mangas!")
                    mangas
                }

                withContext(Dispatchers.Main) {
                    updateDataSet(newManga)
                }
            }
            lastFilterJob = job
            job.join()
        } else {
            updateDataSet(mangas)
        }
    }

    private suspend fun filterManga(queries: List<QueryComponent>, manga: LibraryManga, checkGenre: Boolean = true, searchTags: List<SearchTag>? = null, searchTitles: List<SearchTitle>? = null): Boolean {
        val mappedQueries = queries.groupBy { it.excluded }
        val tracks = if (hasLoggedServices) db.getTracks(manga).await().toList() else null
        val source = sourceManager.get(manga.source)
        val genre = if (checkGenre) manga.getGenres() else null
        val hasNormalQuery = mappedQueries[false]?.all { queryComponent ->
            when (queryComponent) {
                is Text -> {
                    val query = queryComponent.asQuery()
                    manga.title.contains(query, true) ||
                        (manga.author?.contains(query, true) == true) ||
                        (manga.artist?.contains(query, true) == true) ||
                        (source?.name?.contains(query, true) == true) ||
                        (hasLoggedServices && tracks != null && filterTracks(query, tracks)) ||
                        (genre != null && genre.any { it.contains(query, true) }) ||
                        (searchTags != null && searchTags.any { it.name.contains(query, true) }) ||
                        (searchTitles != null && searchTitles.any { it.title.contains(query, true) })
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
                            (manga.author == null || (manga.author?.contains(query, true) == false)) &&
                            (manga.artist == null || (manga.artist?.contains(query, true) == false)) &&
                            (source == null || !source.name.contains(query, true)) &&
                            (hasLoggedServices && tracks != null && !filterTracks(query, tracks)) &&
                            (genre == null || genre.all { !it.contains(query, true) }) &&
                            (searchTags == null || searchTags.all { !it.name.contains(query, true) }) ||
                            (searchTitles == null || searchTitles.all { !it.title.contains(query, true) })
                        )
                }
                is Namespace -> {
                    Timber.d(manga.title)
                    val tag = queryComponent.tag?.asQuery()
                    searchTags == null || searchTags.all {
                        if (tag == null || tag.isBlank()) {
                            it.namespace == null || !it.namespace.contains(queryComponent.namespace, true)
                        } else if (it.namespace == null) {
                            true
                        } else {
                            !(it.name.contains(tag, true) && it.namespace.contains(queryComponent.namespace, true))
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

    private fun filterTracks(constraint: String, tracks: List<Track>): Boolean {
        return tracks.any {
            val trackService = trackManager.getService(it.sync_id)
            if (trackService != null) {
                val status = trackService.getStatus(it.status)
                val name = trackService.name
                return@any status.contains(constraint, true) || name.contains(constraint, true)
            }
            return@any false
        }
    }
    // EXH <--
}
