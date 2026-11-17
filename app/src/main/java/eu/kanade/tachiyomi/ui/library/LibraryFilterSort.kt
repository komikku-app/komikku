package eu.kanade.tachiyomi.ui.library

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.source.MERGED_SOURCE_ID
import exh.source.isMetadataSource
import exh.util.cancellable
import exh.util.isLewd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.Category.Companion.UNCATEGORIZED_ID
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetSearchTags
import tachiyomi.domain.manga.interactor.GetSearchTitles
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random
import tachiyomi.domain.source.model.Source as DomainSource

/*
 * KMK: the library's filter, grouping, sort and text-search logic, moved out of `LibraryScreenModel` so the
 * desktop app runs the same code (borrowed as `desktopCopy`, see app-shared/borrowed-app-sources.gradle.kts and
 * docs/kmp-desktop/PROGRESS.md). `LibraryScreenModel` calls these functions with its own dependencies.
 *
 * Markers: this file is KMK-owned, but the moved function bodies keep the `// SY` / `// KMK` markers they had in
 * `LibraryScreenModel` (and the SY block around the text search), so their provenance stays visible and upstream
 * SY / Mihon changes can still be matched and applied here. They are not new SY code. Everything written for
 * the move (signatures, parameters, `return`s, glue such as `hasActiveLibraryFilters`) uses KMK markers only, and
 * so must any future change. Upstream changes to the moved code must be applied here.
 */

/** Display / filter preferences of library items (formerly `LibraryScreenModel.ItemPreferences`). */
@Immutable
data class LibraryItemPreferences(
    val downloadBadge: Boolean,
    val unreadBadge: Boolean,
    val localBadge: Boolean,
    val languageBadge: Boolean,
    // KMK -->
    val useLangIcon: Boolean,
    val sourceBadge: Boolean,
    // KMK <--
    val skipOutsideReleasePeriod: Boolean,

    val globalFilterDownloaded: Boolean,
    val filterDownloaded: TriState,
    val filterUnread: TriState,
    val filterStarted: TriState,
    val filterBookmarked: TriState,
    val filterCompleted: TriState,
    val filterIntervalCustom: TriState,
    // SY -->
    val filterLewd: TriState,
    // SY <--
    // KMK -->
    val filterCategories: Boolean,
    // KMK <--
)

/** Formerly `LibraryScreenModel.getLibraryItemPreferencesFlow`. */
fun libraryItemPreferencesFlow(
    // KMK -->
    libraryPreferences: LibraryPreferences,
    preferences: BasePreferences,
    // KMK <--
): Flow<LibraryItemPreferences> {
    return combine(
        libraryPreferences.downloadBadge().changes(),
        libraryPreferences.unreadBadge().changes(),
        libraryPreferences.localBadge().changes(),
        libraryPreferences.languageBadge().changes(),
        libraryPreferences.autoUpdateMangaRestrictions().changes(),

        preferences.downloadedOnly().changes(),
        libraryPreferences.filterDownloaded().changes(),
        libraryPreferences.filterUnread().changes(),
        libraryPreferences.filterStarted().changes(),
        libraryPreferences.filterBookmarked().changes(),
        libraryPreferences.filterCompleted().changes(),
        libraryPreferences.filterIntervalCustom().changes(),
        // SY -->
        libraryPreferences.filterLewd().changes(),
        // SY <--
        // KMK -->
        libraryPreferences.sourceBadge().changes(),
        libraryPreferences.useLangIcon().changes(),
        libraryPreferences.filterCategories().changes(),
        // KMK <--
    ) {
        LibraryItemPreferences(
            downloadBadge = it[0] as Boolean,
            unreadBadge = it[1] as Boolean,
            localBadge = it[2] as Boolean,
            languageBadge = it[3] as Boolean,
            skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
            globalFilterDownloaded = it[5] as Boolean,
            filterDownloaded = it[6] as TriState,
            filterUnread = it[7] as TriState,
            filterStarted = it[8] as TriState,
            filterBookmarked = it[9] as TriState,
            filterCompleted = it[10] as TriState,
            filterIntervalCustom = it[11] as TriState,
            // SY -->
            filterLewd = it[12] as TriState,
            // SY <--
            // KMK -->
            sourceBadge = it[13] as Boolean,
            useLangIcon = it[14] as Boolean,
            filterCategories = it[15] as Boolean,
            // KMK <--
        )
    }
}

/**
 * One library item with the badges [preferences] enable (formerly the mapping in
 * `LibraryScreenModel.getFavoritesFlow`).
 */
suspend fun LibraryManga.toLibraryItem(
    preferences: LibraryItemPreferences,
    // KMK -->
    sourceManager: SourceManager,
    downloadManager: DownloadManager,
    getMergedMangaById: GetMergedMangaById,
    // KMK <--
): LibraryItem {
    val manga = this
    // Display mode based on user preference: take it from global library setting or category
    // KMK -->
    val source = sourceManager.getOrStub(manga.manga.source)
    // KMK <--
    return LibraryItem(
        libraryManga = manga,
        downloadCount = if (preferences.downloadBadge) {
            // SY -->
            if (manga.manga.source == MERGED_SOURCE_ID) {
                // FIXME: N+1 performance issues.
                //  Should include all the merged references in library query instead.
                getMergedMangaById.await(manga.manga.id)
                    .sumOf { downloadManager.getDownloadCount(it) }.toLong()
            } else {
                // SY <--
                downloadManager.getDownloadCount(manga.manga).toLong()
            }
        } else {
            0
        },
        unreadCount = if (preferences.unreadBadge) {
            manga.unreadCount
        } else {
            0
        },
        isLocal = if (preferences.localBadge) {
            manga.manga.isLocal()
        } else {
            false
        },
        sourceLanguage = if (preferences.languageBadge) {
            sourceManager.getOrStub(manga.manga.source).lang
        } else {
            ""
        },
        // KMK -->
        useLangIcon = preferences.useLangIcon,
        source = if (preferences.sourceBadge) {
            DomainSource(
                source.id,
                source.lang,
                source.name,
                supportsLatest = false,
                isStub = source is StubSource,
            )
        } else {
            null
        },
        // KMK <--
    )
}

/**
 * Flow of tracking filter preferences
 *
 * @return map of track id with the filter value
 */
fun libraryTrackingFiltersFlow(
    // KMK -->
    trackerManager: TrackerManager,
    libraryPreferences: LibraryPreferences,
    // KMK <--
): Flow<Map<Long, TriState>> {
    return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
        if (loggedInTrackers.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val filterFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
            }
            combine(filterFlows) { it.toMap() }
        }
    }
}

/** Whether a library filter is active (formerly inline in `LibraryScreenModel`'s init). */
fun hasActiveLibraryFilters(prefs: LibraryItemPreferences, trackFilters: Map<Long, TriState>): Boolean {
    return listOf(
        prefs.filterDownloaded,
        prefs.filterUnread,
        prefs.filterStarted,
        prefs.filterBookmarked,
        prefs.filterCompleted,
        prefs.filterIntervalCustom,
        // SY -->
        prefs.filterLewd,
        // SY <--
        *trackFilters.values.toTypedArray(),
    )
        .fastAny { it != TriState.DISABLED } ||
        // KMK -->
        prefs.filterCategories
    // KMK <--
}

/** Formerly `LibraryScreenModel.applyFilters`. */
suspend fun List<LibraryItem>.applyLibraryFilters(
    trackMap: Map<Long, List<Track>>,
    trackingFilter: Map<Long, TriState>,
    preferences: LibraryItemPreferences,
    // KMK -->
    includedCategories: Set<Long>,
    excludedCategories: Set<Long>,
    downloadManager: DownloadManager,
    getMergedMangaById: GetMergedMangaById,
    // KMK <--
): List<LibraryItem> {
    val downloadedOnly = preferences.globalFilterDownloaded
    val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
    val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
    val filterUnread = preferences.filterUnread
    val filterStarted = preferences.filterStarted
    val filterBookmarked = preferences.filterBookmarked
    val filterCompleted = preferences.filterCompleted
    val filterIntervalCustom = preferences.filterIntervalCustom
    val filterCategories = preferences.filterCategories

    val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

    val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
    val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
    val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

    // SY -->
    val filterLewd = preferences.filterLewd
    // SY <--

    val filterFnDownloaded: suspend (LibraryItem) -> Boolean = {
        applyFilter(filterDownloaded) {
            it.libraryManga.manga.isLocal() ||
                it.downloadCount > 0 ||
                // KMK -->
                if (it.libraryManga.manga.source == MERGED_SOURCE_ID) {
                    // FIXME: Calling await in filter could lead to N+1 performance issues.
                    //  Should include all the merged references in library query instead.
                    getMergedMangaById.await(it.libraryManga.manga.id)
                        .sumOf { manga -> downloadManager.getDownloadCount(manga) } > 0
                } else {
                    // KMK <--
                    downloadManager.getDownloadCount(it.libraryManga.manga) > 0
                }
        }
    }

    val filterFnUnread: (LibraryItem) -> Boolean = {
        applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
    }

    val filterFnStarted: (LibraryItem) -> Boolean = {
        applyFilter(filterStarted) { it.libraryManga.hasStarted }
    }

    val filterFnBookmarked: (LibraryItem) -> Boolean = {
        applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
    }

    val filterFnCompleted: (LibraryItem) -> Boolean = {
        applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
    }

    val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
        if (skipOutsideReleasePeriod) {
            applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
        } else {
            true
        }
    }

    // SY -->
    val filterFnLewd: (LibraryItem) -> Boolean = {
        applyFilter(filterLewd) { it.libraryManga.manga.isLewd() }
    }
    // SY <--

    val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
        if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

        // KMK -->
        val mangaTracks = trackMap[item.id].orEmpty()

        val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it.trackerId in excludedTracks }
        val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it.trackerId in includedTracks }
        // KMK <--

        !isExcluded && isIncluded
    }

    // KMK -->
    val filterFnCategories: (LibraryItem) -> Boolean = categories@{ item ->
        if (!filterCategories) return@categories true

        val mangaCategories = item.libraryManga.categories.fastFilterNot { it == 0L }.toSet()

        // Early return
        if (mangaCategories.isEmpty()) {
            return@categories includedCategories.isEmpty()
        }

        val isExcluded = excludedCategories.any { it in mangaCategories }
        val isIncluded = includedCategories.isEmpty() || includedCategories.all { it in mangaCategories }

        !isExcluded && isIncluded
    }
    // KMK <--

    return fastFilter {
        filterFnDownloaded(it) &&
            filterFnUnread(it) &&
            filterFnStarted(it) &&
            filterFnBookmarked(it) &&
            filterFnCompleted(it) &&
            filterFnIntervalCustom(it) &&
            filterFnTracking(it) &&
            // SY -->
            filterFnLewd(it) &&
            // SY <--
            // KMK -->
            filterFnCategories(it)
        // KMK <--
    }
}

/** Grouping by category (formerly the `LibraryGroup.BY_DEFAULT` branch of `LibraryScreenModel.applyGrouping`). */
fun List<LibraryItem>.groupByDefaultCategory(
    categories: List<Category>,
    // KMK -->
    showHiddenCategories: Boolean,
    // KMK <--
): Map<Category, List</* LibraryItem */ Long>> {
    // KMK -->
    var showSystemCategory = false
    // KMK <--
    val groupCache = mutableMapOf</* Category.id */ Long, MutableList</* LibraryItem */ Long>>()
    forEach { item ->
        item.libraryManga.categories.forEach { categoryId ->
            // KMK -->
            if (categoryId == UNCATEGORIZED_ID) {
                showSystemCategory = true
            }
            // KMK <--
            groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
        }
    }
    return categories.fastFilter {
        (showSystemCategory || !it.isSystemCategory) &&
            // KMK -->
            (showHiddenCategories || !it.hidden)
        // KMK <--
    }
        .associateWith {
            groupCache[it.id]?.toList()
                // KMK -->
                ?.distinct()
                // KMK <--
                .orEmpty()
        }
}

/** Formerly `LibraryScreenModel.applySort`. */
fun Map<Category, List</* LibraryItem */ Long>>.applyLibrarySort(
    favoritesById: Map<Long, LibraryItem>,
    trackMap: Map<Long, List<Track>>,
    loggedInTrackerIds: Set<Long>,
    // SY -->
    // KMK -->
    // No default (was `= null` in LibraryScreenModel): callers pass it explicitly
    // KMK <--
    groupSort: LibrarySort?,
    // SY <--
    // KMK -->
    libraryPreferences: LibraryPreferences,
    trackerManager: TrackerManager,
    // KMK <--
): Map<Category, List</* LibraryItem */ Long>> {
    // SY -->
    val listOfTags by lazy {
        libraryPreferences.sortTagsForLibrary().get()
            .asSequence()
            .mapNotNull {
                val list = it.split("|")
                (list.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to
                    (list.getOrNull(1) ?: return@mapNotNull null)
            }
            .sortedBy { it.first }
            .map { it.second }
            .toList()
    }
    // SY <--

    val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
        val title1 = manga1.libraryManga.manga.title.lowercase()
        val title2 = manga2.libraryManga.manga.title.lowercase()
        title1.compareToWithCollator(title2)
    }

    val defaultTrackerScoreSortValue = -1.0
    val trackerScores by lazy {
        val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
        trackMap.mapValues { entry ->
            when {
                entry.value.isEmpty() -> null
                else ->
                    entry.value
                        .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                        .average()
            }
        }
    }

    fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
        // SY -->
        val sort = groupSort ?: this
        // SY <--
        when (sort.type) {
            LibrarySort.Type.Alphabetical -> {
                sortAlphabetically(manga1, manga2)
            }
            LibrarySort.Type.LastRead -> {
                manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
            }
            LibrarySort.Type.LastUpdate -> {
                manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
            }
            LibrarySort.Type.UnreadCount -> when {
                // Ensure unread content comes first
                manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                manga1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                manga2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
            }
            LibrarySort.Type.TotalChapters -> {
                manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
            }
            LibrarySort.Type.LatestChapter -> {
                manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
            }
            LibrarySort.Type.ChapterFetchDate -> {
                manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
            }
            LibrarySort.Type.DateAdded -> {
                manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
            }
            LibrarySort.Type.TrackerMean -> {
                val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                item1Score.compareTo(item2Score)
            }
            LibrarySort.Type.Random -> {
                error("Why Are We Still Here? Just To Suffer?")
            }
            // SY -->
            LibrarySort.Type.TagList -> {
                val manga1IndexOfTag = listOfTags.indexOfFirst {
                    manga1.libraryManga.manga.genre?.contains(it) ?: false
                }
                val manga2IndexOfTag = listOfTags.indexOfFirst {
                    manga2.libraryManga.manga.genre?.contains(it) ?: false
                }
                manga1IndexOfTag.compareTo(manga2IndexOfTag)
            }
            // SY <--
        }
    }

    return mapValues { (key, value) ->
        // SY -->
        val sort = groupSort ?: key.sort
        if (sort.type == LibrarySort.Type.Random) {
            // SY <--
            return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed().get()))
        }

        val manga = value.mapNotNull { favoritesById[it] }

        // SY -->
        val comparator = sort.comparator()
            // SY <--
            .let { if (/* SY --> */ sort.isAscending /* SY <-- */) it else it.reversed() }
            .thenComparator(sortAlphabetically)

        manga.sortedWith(comparator).map { it.id }
    }
}

// SY -->
/**
 * Formerly the query part of `LibraryScreenModel.filterLibrary` (the AZ `mangadex-dmca` easter egg stays there):
 * `id:` / `src:` queries, else the [SearchEngine]-parsed query matched by [filterManga].
 */
suspend fun filterLibraryByQuery(
    unfiltered: List<LibraryItem>,
    query: String?,
    loggedInTrackServices: Map<Long, TriState>,
    // KMK -->
    searchEngine: SearchEngine,
    getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata,
    getTracks: GetTracks,
    sourceManager: SourceManager,
    getSearchTags: GetSearchTags,
    getSearchTitles: GetSearchTitles,
    trackerManager: TrackerManager,
    // KMK <--
): List<LibraryItem> {
    // KMK -->
    if (unfiltered.isEmpty() || query.isNullOrBlank()) return unfiltered
    // KMK <--
    // Prepare filter object
    val parsedQuery = searchEngine.parseQuery(query)
    val mangaWithMetaIds = getIdsOfFavoriteMangaWithMetadata.await()
    val tracks = if (loggedInTrackServices.isNotEmpty()) {
        getTracks.await().groupBy { it.mangaId }
    } else {
        emptyMap()
    }
    val sources = unfiltered
        .distinctBy { it.libraryManga.manga.source }
        .fastMapNotNull { sourceManager.get(it.libraryManga.manga.source) }
        .associateBy { it.id }
    return unfiltered.asFlow().cancellable().filter { item ->
        val mangaId = item.libraryManga.manga.id
        if (query.startsWith("id:", true)) {
            return@filter mangaId == query.substringAfter("id:").toLongOrNull()
        }
        val sourceId = item.libraryManga.manga.source
        if (query.startsWith("src:", true)) {
            val querySource = query.substringAfter("src:")
            return@filter if (querySource.equals(LOCAL_SOURCE_ID_ALIAS, ignoreCase = true)) {
                sourceId == LocalSource.ID
            } else {
                sourceId == querySource.toLongOrNull()
            }
        }
        if (isMetadataSource(sourceId) && mangaWithMetaIds.binarySearch(mangaId) >= 0) {
            val tags = getSearchTags.await(mangaId)
            val titles = getSearchTitles.await(mangaId)
            filterManga(
                queries = parsedQuery,
                libraryManga = item.libraryManga,
                tracks = tracks[mangaId],
                source = sources[sourceId],
                checkGenre = false,
                searchTags = tags,
                searchTitles = titles,
                loggedInTrackServices = loggedInTrackServices,
                // KMK -->
                trackerManager = trackerManager,
                // KMK <--
            )
        } else {
            // No meta? Filter using title
            filterManga(
                queries = parsedQuery,
                libraryManga = item.libraryManga,
                tracks = tracks[mangaId],
                source = sources[sourceId],
                loggedInTrackServices = loggedInTrackServices,
                // KMK -->
                trackerManager = trackerManager,
                // KMK <--
            )
        }
    }.toList()
}

/** Whether [libraryManga] matches every query component (formerly `LibraryScreenModel.filterManga`). */
private fun filterManga(
    queries: List<QueryComponent>,
    libraryManga: LibraryManga,
    tracks: List<Track>?,
    source: Source?,
    checkGenre: Boolean = true,
    searchTags: List<SearchTag>? = null,
    searchTitles: List<SearchTitle>? = null,
    loggedInTrackServices: Map<Long, TriState>,
    // KMK -->
    trackerManager: TrackerManager,
    // KMK <--
): Boolean {
    val manga = libraryManga.manga
    val sourceIdString = manga.source.takeUnless { it == LocalSource.ID }?.toString()
    val genre = if (checkGenre) manga.genre.orEmpty() else emptyList()
    val context = Injekt.get<Application>()
    return queries.all { queryComponent ->
        when (queryComponent.excluded) {
            false -> when (queryComponent) {
                is Text -> {
                    val query = queryComponent.asQuery()
                    manga.title.contains(query, true) ||
                        (manga.author?.contains(query, true) == true) ||
                        (manga.artist?.contains(query, true) == true) ||
                        (manga.description?.contains(query, true) == true) ||
                        (source?.name?.contains(query, true) == true) ||
                        (sourceIdString != null && sourceIdString == query) ||
                        (
                            loggedInTrackServices.isNotEmpty() &&
                                tracks != null &&
                                filterTracks(
                                    query,
                                    tracks,
                                    context,
                                    // KMK -->
                                    trackerManager,
                                    // KMK <--
                                )
                            ) ||
                        (genre.fastAny { it.contains(query, true) }) ||
                        (searchTags?.fastAny { it.name.contains(query, true) } == true) ||
                        (searchTitles?.fastAny { it.title.contains(query, true) } == true)
                }
                is Namespace -> {
                    searchTags != null &&
                        searchTags.fastAny {
                            val tag = queryComponent.tag
                            (
                                it.namespace.equals(queryComponent.namespace, true) &&
                                    tag?.run { it.name.contains(tag.asQuery(), true) } == true
                                ) ||
                                (tag == null && it.namespace.equals(queryComponent.namespace, true))
                        }
                }
                else -> true
            }
            true -> when (queryComponent) {
                is Text -> {
                    val query = queryComponent.asQuery()
                    query.isBlank() ||
                        (
                            (!manga.title.contains(query, true)) &&
                                (manga.author?.contains(query, true) != true) &&
                                (manga.artist?.contains(query, true) != true) &&
                                (manga.description?.contains(query, true) != true) &&
                                (source?.name?.contains(query, true) != true) &&
                                (sourceIdString != null && sourceIdString != query) &&
                                (
                                    loggedInTrackServices.isEmpty() ||
                                        tracks == null ||
                                        !filterTracks(
                                            query,
                                            tracks,
                                            context,
                                            // KMK -->
                                            trackerManager,
                                            // KMK <--
                                        )
                                    ) &&
                                (!genre.fastAny { it.contains(query, true) }) &&
                                (searchTags?.fastAny { it.name.contains(query, true) } != true) &&
                                (searchTitles?.fastAny { it.title.contains(query, true) } != true)
                            )
                }
                is Namespace -> {
                    val searchedTag = queryComponent.tag?.asQuery()
                    searchTags == null ||
                        (queryComponent.namespace.isBlank() && searchedTag.isNullOrBlank()) ||
                        searchTags.fastAll { mangaTag ->
                            if (queryComponent.namespace.isBlank() && !searchedTag.isNullOrBlank()) {
                                !mangaTag.name.contains(searchedTag, true)
                            } else if (searchedTag.isNullOrBlank()) {
                                mangaTag.namespace == null ||
                                    !mangaTag.namespace.equals(queryComponent.namespace, true)
                            } else if (mangaTag.namespace.isNullOrBlank()) {
                                true
                            } else {
                                !mangaTag.name.contains(searchedTag, true) ||
                                    !mangaTag.namespace.equals(queryComponent.namespace, true)
                            }
                        }
                }
                else -> true
            }
        }
    }
}

private fun filterTracks(
    constraint: String,
    tracks: List<Track>,
    context: Context,
    // KMK -->
    trackerManager: TrackerManager,
    // KMK <--
): Boolean {
    return tracks.fastAny { track ->
        val trackService = trackerManager.get(track.trackerId)
        if (trackService != null) {
            val status = trackService.getStatus(track.status)?.let {
                context.stringResource(it)
            }
            val name = trackerManager.get(track.trackerId)?.name
            status?.contains(constraint, true) == true || name?.contains(constraint, true) == true
        } else {
            false
        }
    }
}
// SY <--
