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
 * KMK: the library's filter, sort and text-search logic, moved out of `LibraryScreenModel` with unchanged
 * bodies so the desktop app runs the same code (borrowed as `desktopCopy`, see
 * app-shared/borrowed-app-sources.gradle.kts and docs/kmp-desktop/PROGRESS.md). `LibraryScreenModel` calls
 * these functions with its own dependencies.
 */

/** Display / filter preferences of library items (formerly `LibraryScreenModel.ItemPreferences`). */
@Immutable
data class LibraryItemPreferences(
    val downloadBadge: Boolean,
    val unreadBadge: Boolean,
    val localBadge: Boolean,
    val languageBadge: Boolean,
    val useLangIcon: Boolean,
    val sourceBadge: Boolean,
    val skipOutsideReleasePeriod: Boolean,

    val globalFilterDownloaded: Boolean,
    val filterDownloaded: TriState,
    val filterUnread: TriState,
    val filterStarted: TriState,
    val filterBookmarked: TriState,
    val filterCompleted: TriState,
    val filterIntervalCustom: TriState,
    val filterLewd: TriState,
    val filterCategories: Boolean,
)

/** [LibraryItemPreferences] from the library / base preferences (formerly `getLibraryItemPreferencesFlow`). */
fun libraryItemPreferencesFlow(
    libraryPreferences: LibraryPreferences,
    preferences: BasePreferences,
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
        libraryPreferences.filterLewd().changes(),
        libraryPreferences.sourceBadge().changes(),
        libraryPreferences.useLangIcon().changes(),
        libraryPreferences.filterCategories().changes(),
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
            filterLewd = it[12] as TriState,
            sourceBadge = it[13] as Boolean,
            useLangIcon = it[14] as Boolean,
            filterCategories = it[15] as Boolean,
        )
    }
}

/**
 * Flow of tracking filter preferences (formerly `getTrackingFiltersFlow`)
 *
 * @return map of track id with the filter value
 */
fun libraryTrackingFiltersFlow(
    trackerManager: TrackerManager,
    libraryPreferences: LibraryPreferences,
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

/** The library item of [this] with the badges [preferences] enable (formerly in `getFavoritesFlow`). */
suspend fun LibraryManga.toLibraryItem(
    preferences: LibraryItemPreferences,
    sourceManager: SourceManager,
    downloadManager: DownloadManager,
    getMergedMangaById: GetMergedMangaById,
): LibraryItem {
    val manga = this
    // Display mode based on user preference: take it from global library setting or category
    val source = sourceManager.getOrStub(manga.manga.source)
    return LibraryItem(
        libraryManga = manga,
        downloadCount = if (preferences.downloadBadge) {
            if (manga.manga.source == MERGED_SOURCE_ID) {
                // FIXME: N+1 performance issues.
                //  Should include all the merged references in library query instead.
                getMergedMangaById.await(manga.manga.id)
                    .sumOf { downloadManager.getDownloadCount(it) }.toLong()
            } else {
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
    )
}

/** Applies the library filters (downloaded, unread, started, bookmarked, completed, ..., trackers, categories). */
suspend fun List<LibraryItem>.applyLibraryFilters(
    trackMap: Map<Long, List<Track>>,
    trackingFilter: Map<Long, TriState>,
    preferences: LibraryItemPreferences,
    includedCategories: Set<Long>,
    excludedCategories: Set<Long>,
    downloadManager: DownloadManager,
    getMergedMangaById: GetMergedMangaById,
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

    val filterLewd = preferences.filterLewd

    val filterFnDownloaded: suspend (LibraryItem) -> Boolean = {
        applyFilter(filterDownloaded) {
            it.libraryManga.manga.isLocal() ||
                it.downloadCount > 0 ||
                if (it.libraryManga.manga.source == MERGED_SOURCE_ID) {
                    // FIXME: Calling await in filter could lead to N+1 performance issues.
                    //  Should include all the merged references in library query instead.
                    getMergedMangaById.await(it.libraryManga.manga.id)
                        .sumOf { manga -> downloadManager.getDownloadCount(manga) } > 0
                } else {
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

    val filterFnLewd: (LibraryItem) -> Boolean = {
        applyFilter(filterLewd) { it.libraryManga.manga.isLewd() }
    }

    val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
        if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

        val mangaTracks = trackMap[item.id].orEmpty()

        val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it.trackerId in excludedTracks }
        val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it.trackerId in includedTracks }

        !isExcluded && isIncluded
    }

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

    return fastFilter {
        filterFnDownloaded(it) &&
            filterFnUnread(it) &&
            filterFnStarted(it) &&
            filterFnBookmarked(it) &&
            filterFnCompleted(it) &&
            filterFnIntervalCustom(it) &&
            filterFnTracking(it) &&
            filterFnLewd(it) &&
            filterFnCategories(it)
    }
}

/**
 * Sorts each category's items by the category's sort ([groupSort] when grouping is not by category), then
 * alphabetically.
 */
fun Map<Category, List</* LibraryItem */ Long>>.applyLibrarySort(
    favoritesById: Map<Long, LibraryItem>,
    trackMap: Map<Long, List<Track>>,
    loggedInTrackerIds: Set<Long>,
    groupSort: LibrarySort?,
    libraryPreferences: LibraryPreferences,
    trackerManager: TrackerManager,
): Map<Category, List</* LibraryItem */ Long>> {
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
        val sort = groupSort ?: this
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
            LibrarySort.Type.TagList -> {
                val manga1IndexOfTag = listOfTags.indexOfFirst {
                    manga1.libraryManga.manga.genre?.contains(it) ?: false
                }
                val manga2IndexOfTag = listOfTags.indexOfFirst {
                    manga2.libraryManga.manga.genre?.contains(it) ?: false
                }
                manga1IndexOfTag.compareTo(manga2IndexOfTag)
            }
        }
    }

    return mapValues { (key, value) ->
        val sort = groupSort ?: key.sort
        if (sort.type == LibrarySort.Type.Random) {
            return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed().get()))
        }

        val manga = value.mapNotNull { favoritesById[it] }

        val comparator = sort.comparator()
            .let { if (sort.isAscending) it else it.reversed() }
            .thenComparator(sortAlphabetically)

        manga.sortedWith(comparator).map { it.id }
    }
}

/**
 * Library text search (SY): `id:<manga id>`, `src:<source id | local>`, else the query parsed by
 * [SearchEngine] (terms, quoted phrases, `-` exclusions, `namespace:tag`) matched against title, author,
 * artist, description, source name / id, tracker name / status, genres and the SY search tags / titles of
 * metadata sources.
 */
suspend fun filterLibraryByQuery(
    unfiltered: List<LibraryItem>,
    query: String?,
    loggedInTrackServices: Map<Long, TriState>,
    searchEngine: SearchEngine,
    getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata,
    getTracks: GetTracks,
    sourceManager: SourceManager,
    getSearchTags: GetSearchTags,
    getSearchTitles: GetSearchTitles,
    trackerManager: TrackerManager,
): List<LibraryItem> {
    if (unfiltered.isEmpty() || query.isNullOrBlank()) return unfiltered
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
            libraryMangaMatches(
                queries = parsedQuery,
                libraryManga = item.libraryManga,
                tracks = tracks[mangaId],
                source = sources[sourceId],
                checkGenre = false,
                searchTags = tags,
                searchTitles = titles,
                loggedInTrackServices = loggedInTrackServices,
                trackerManager = trackerManager,
            )
        } else {
            // No meta? Filter using title
            libraryMangaMatches(
                queries = parsedQuery,
                libraryManga = item.libraryManga,
                tracks = tracks[mangaId],
                source = sources[sourceId],
                loggedInTrackServices = loggedInTrackServices,
                trackerManager = trackerManager,
            )
        }
    }.toList()
}

/** Whether [libraryManga] matches every parsed query component (formerly `LibraryScreenModel.filterManga`). */
fun libraryMangaMatches(
    queries: List<QueryComponent>,
    libraryManga: LibraryManga,
    tracks: List<Track>?,
    source: Source?,
    checkGenre: Boolean = true,
    searchTags: List<SearchTag>? = null,
    searchTitles: List<SearchTitle>? = null,
    loggedInTrackServices: Map<Long, TriState>,
    trackerManager: TrackerManager,
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
                                filterTracks(query, tracks, context, trackerManager)
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
                                        !filterTracks(query, tracks, context, trackerManager)
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
    trackerManager: TrackerManager,
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
