package eu.kanade.tachiyomi.ui.library

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.anime.interactor.SmartSearchMerge
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeCovers
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.source.EH_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedAnime
import exh.source.isMetadataSource
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import exh.util.cancellable
import exh.util.isLewd
import exh.util.nullIfBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.anime.interactor.GetIdsOfFavoriteAnimeWithMetadata
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.GetMergedAnimeById
import tachiyomi.domain.anime.interactor.GetSearchTags
import tachiyomi.domain.anime.interactor.GetSearchTitles
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.GetMergedEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerAnime
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random
import tachiyomi.domain.source.model.Source as DomainSource

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
typealias LibraryMap = Map<Category, List<LibraryItem>>

class LibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    // SY -->
    private val unsortedPreferences: UnsortedPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getMergedAnimeById: GetMergedAnimeById = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getIdsOfFavoriteAnimeWithMetadata: GetIdsOfFavoriteAnimeWithMetadata = Injekt.get(),
    private val getSearchTags: GetSearchTags = Injekt.get(),
    private val getSearchTitles: GetSearchTitles = Injekt.get(),
    private val searchEngine: SearchEngine = Injekt.get(),
    private val setCustomAnimeInfo: SetCustomAnimeInfo = Injekt.get(),
    private val getMergedEpisodesByAnimeId: GetMergedEpisodesByAnimeId = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    // SY <--
    // KMK -->
    private val smartSearchMerge: SmartSearchMerge = Injekt.get(),
    // KMK <--
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedCategory().asState(screenModelScope)

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerAnime.subscribe(),
                combine(
                    getTrackingFilterFlow(),
                    downloadCache.changes,
                    ::Pair,
                ),
                // SY -->
                combine(
                    state.map { it.groupType }.distinctUntilChanged(),
                    libraryPreferences.sortingMode().changes(),
                    ::Pair,
                ),
                // SY <--
            ) { searchQuery, library, tracks, (trackingFilter, _), (groupType, sort) ->
                library
                    // SY -->
                    .applyGrouping(groupType)
                    // SY <--
                    .applyFilters(tracks, trackingFilter)
                    .applySort(
                        tracks,
                        trackingFilter.keys,
                        // SY -->
                        sort.takeIf { groupType != LibraryGroup.BY_DEFAULT },
                        // SY <--
                    )
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            // Filter query
                            // SY -->
                            filterLibrary(value, searchQuery, trackingFilter)
                            // SY <--
                        } else {
                            // Don't do anything
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueWatchingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnread,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                    prefs.filterCompleted,
                    prefs.filterIntervalCustom,
                    // SY -->
                    prefs.filterLewd,
                    // SY <--
                ) + trackFilter.values
                ).any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        // SY -->
        combine(
            unsortedPreferences.isHentaiEnabled().changes(),
            sourcePreferences.disabledSources().changes(),
            unsortedPreferences.enableExhentai().changes(),
        ) { isHentaiEnabled, disabledSources, enableExhentai ->
            isHentaiEnabled && (EH_SOURCE_ID.toString() !in disabledSources || enableExhentai)
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(showSyncExh = it)
                }
            }
            .launchIn(screenModelScope)

        libraryPreferences.groupLibraryBy().changes()
            .onEach {
                mutableState.update { state ->
                    state.copy(groupType = it)
                }
            }
            .launchIn(screenModelScope)
        syncPreferences.syncService()
            .changes()
            .distinctUntilChanged()
            .onEach { syncService ->
                mutableState.update { it.copy(isSyncEnabled = syncService != 0) }
            }
            .launchIn(screenModelScope)
        // SY <--
    }

    /**
     * Applies library filters to the given map of manga.
     */
    private suspend fun LibraryMap.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
    ): LibraryMap {
        val prefs = getLibraryItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        // SY -->
        val filterLewd = prefs.filterLewd
        // SY <--

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryAnime.anime.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryAnime.anime) > 0
            }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryAnime.anime.fetchInterval < 0 }
            } else {
                true
            }
        }

        // SY -->
        val filterFnLewd: (LibraryItem) -> Boolean = {
            applyFilter(filterLewd) { it.libraryAnime.anime.isLewd() }
        }
        // SY <--

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryAnime.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (LibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it) &&
                // SY -->
                filterFnLewd(it)
            // SY <--
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    /**
     * Applies library sorting to the given map of manga.
     */
    private fun LibraryMap.applySort(
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
        // SY -->
        groupSort: LibrarySort? = null,
        // SY <--
    ): LibraryMap {
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

        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            i1.libraryAnime.anime.title.lowercase().compareToWithCollator(i2.libraryAnime.anime.title.lowercase())
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

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { i1, i2 ->
            // SY -->
            val sort = groupSort ?: this
            // SY <--
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                LibrarySort.Type.LastSeen -> {
                    i1.libraryAnime.lastSeen.compareTo(i2.libraryAnime.lastSeen)
                }
                LibrarySort.Type.LastUpdate -> {
                    i1.libraryAnime.anime.lastUpdate.compareTo(i2.libraryAnime.anime.lastUpdate)
                }
                LibrarySort.Type.UnseenCount -> when {
                    // Ensure unread content comes first
                    i1.libraryAnime.unseenCount == i2.libraryAnime.unseenCount -> 0
                    i1.libraryAnime.unseenCount == 0L -> if (sort.isAscending) 1 else -1
                    i2.libraryAnime.unseenCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                }
                LibrarySort.Type.TotalEpisodes -> {
                    i1.libraryAnime.totalEpisodes.compareTo(i2.libraryAnime.totalEpisodes)
                }
                LibrarySort.Type.LatestEpisode -> {
                    i1.libraryAnime.latestUpload.compareTo(i2.libraryAnime.latestUpload)
                }
                LibrarySort.Type.EpisodeFetchDate -> {
                    i1.libraryAnime.episodeFetchedAt.compareTo(i2.libraryAnime.episodeFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    i1.libraryAnime.anime.dateAdded.compareTo(i2.libraryAnime.anime.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                // SY -->
                LibrarySort.Type.TagList -> {
                    val manga1IndexOfTag = listOfTags.indexOfFirst {
                        i1.libraryAnime.anime.genre?.contains(it) ?: false
                    }
                    val manga2IndexOfTag = listOfTags.indexOfFirst {
                        i2.libraryAnime.anime.genre?.contains(it) ?: false
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
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed().get()))
            }
            val comparator = sort.comparator()
                // SY <--
                .let { if (/* SY --> */ sort.isAscending /* SY <-- */) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            value.sortedWith(comparator)
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateAnimeRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloaded().changes(),
            libraryPreferences.filterUnseen().changes(),
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
            // KMK <--
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                localBadge = it[1] as Boolean,
                languageBadge = it[2] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in (it[3] as Set<*>),
                globalFilterDownloaded = it[4] as Boolean,
                filterDownloaded = it[5] as TriState,
                filterUnread = it[6] as TriState,
                filterStarted = it[7] as TriState,
                filterBookmarked = it[8] as TriState,
                filterCompleted = it[9] as TriState,
                filterIntervalCustom = it[10] as TriState,
                // SY -->
                filterLewd = it[11] as TriState,
                // SY <--
                // KMK -->
                sourceBadge = it[12] as Boolean,
                useLangIcon = it[13] as Boolean,
                // KMK <--
            )
        }
    }

    /**
     * Get the categories and all its manga from the database.
     */
    private fun getLibraryFlow(): Flow<LibraryMap> {
        val libraryMangasFlow = combine(
            getLibraryAnime.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryMangaList, prefs, _ ->
            libraryMangaList
                .map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    // KMK -->
                    val source = sourceManager.getOrStub(libraryManga.anime.source)
                    // KMK <--
                    LibraryItem(
                        libraryManga,
                        downloadCount = if (prefs.downloadBadge) {
                            // SY -->
                            if (libraryManga.anime.source == MERGED_SOURCE_ID) {
                                runBlocking {
                                    getMergedAnimeById.await(libraryManga.anime.id)
                                }.sumOf { downloadManager.getDownloadCount(it) }.toLong()
                            } else {
                                downloadManager.getDownloadCount(libraryManga.anime).toLong()
                            }
                            // SY <--
                        } else {
                            0
                        },
                        unseenCount = libraryManga.unseenCount,
                        isLocal = if (prefs.localBadge) libraryManga.anime.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            source.lang
                        } else {
                            ""
                        },
                        // KMK -->
                        useLangIcon = prefs.useLangIcon,
                        source = if (prefs.sourceBadge) {
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
                .groupBy { it.libraryAnime.category }
        }

        return combine(
            // KMK -->
            libraryPreferences.showHiddenCategories().changes(),
            // KMK <--
            getCategories.subscribe(),
            libraryMangasFlow,
        ) { showHiddenCategories, categories, libraryManga ->
            val displayCategories = if (libraryManga.isNotEmpty() && !libraryManga.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            // SY -->
            mutableState.update { state ->
                state.copy(ogCategories = displayCategories)
            }
            // SY <--
            displayCategories
                // KMK -->
                .filterNot { !showHiddenCategories && it.hidden }
                // KMK <--
                .associateWith { libraryManga[it.id].orEmpty() }
        }
    }

    // SY -->
    private fun LibraryMap.applyGrouping(groupType: Int): LibraryMap {
        val items = when (groupType) {
            LibraryGroup.BY_DEFAULT -> this
            LibraryGroup.UNGROUPED -> {
                mapOf(
                    Category(
                        0,
                        preferences.context.stringResource(SYMR.strings.ungrouped),
                        0,
                        0,
                        // KMK -->
                        false,
                        // KMK <--
                    ) to
                        values.flatten().distinctBy { it.libraryAnime.anime.id },
                )
            }
            else -> {
                getGroupedMangaItems(
                    groupType = groupType,
                    libraryManga = this.values.flatten().distinctBy { it.libraryAnime.anime.id },
                )
            }
        }

        return items
    }
    // SY <--

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTracking(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Anime>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Anime): Episode? {
        // SY -->
        val mergedManga = getMergedAnimeById.await(manga.id).associateBy { it.id }
        return if (manga.id == MERGED_SOURCE_ID) {
            getMergedEpisodesByAnimeId.await(manga.id, applyScanlatorFilter = true)
        } else {
            getEpisodesByAnimeId.await(manga.id, applyScanlatorFilter = true)
        }.getNextUnseen(manga, downloadManager, mergedManga)
        // SY <--
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Anime>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val mangas = selection.map { it.anime }.toList()
        when (action) {
            DownloadAction.NEXT_1_EPISODE -> downloadUnreadChapters(mangas, 1)
            DownloadAction.NEXT_5_EPISODES -> downloadUnreadChapters(mangas, 5)
            DownloadAction.NEXT_10_EPISODES -> downloadUnreadChapters(mangas, 10)
            DownloadAction.NEXT_25_EPISODES -> downloadUnreadChapters(mangas, 25)
            DownloadAction.UNSEEN_EPISODES -> downloadUnreadChapters(mangas, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unread episodes from the list of mangas given.
     *
     * @param mangas the list of manga.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnreadChapters(mangas: List<Anime>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                // SY -->
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedMangas = getMergedAnimeById.await(manga.id)
                        .associateBy { it.id }
                    getNextEpisodes.await(manga.id)
                        .let { if (amount != null) it.take(amount) else it }
                        .groupBy { it.animeId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas[mangaId] ?: return@ab
                            val downloadChapters = chapters.fastFilterNot { chapter ->
                                downloadManager.queueState.value.fastAny { chapter.id == it.episode.id } ||
                                    downloadManager.isEpisodeDownloaded(
                                        chapter.name,
                                        chapter.scanlator,
                                        mergedManga.ogTitle,
                                        mergedManga.source,
                                    )
                            }

                            downloadManager.downloadEpisodes(mergedManga, downloadChapters)
                        }

                    return@forEach
                }

                // SY <--
                val chapters = getNextEpisodes.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                // SY -->
                                manga.ogTitle,
                                // SY <--
                                manga.source,

                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(manga, chapters)
            }
        }
    }

    // SY -->
    fun cleanTitles() {
        state.value.selection.fastFilter {
            it.anime.isEhBasedAnime() ||
                it.anime.source in nHentaiSourceIds
        }.fastForEach { (manga) ->
            val editedTitle = manga.title.replace("\\[.*?]".toRegex(), "").trim().replace("\\(.*?\\)".toRegex(), "").trim().replace("\\{.*?\\}".toRegex(), "").trim().let {
                if (it.contains("|")) {
                    it.replace(".*\\|".toRegex(), "").trim()
                } else {
                    it
                }
            }
            if (manga.title == editedTitle) return@fastForEach
            val mangaInfo = CustomAnimeInfo(
                id = manga.id,
                title = editedTitle.nullIfBlank(),
                author = manga.author.takeUnless { it == manga.ogAuthor },
                artist = manga.artist.takeUnless { it == manga.ogArtist },
                thumbnailUrl = manga.thumbnailUrl.takeUnless { it == manga.ogThumbnailUrl },
                description = manga.description.takeUnless { it == manga.ogDescription },
                genre = manga.genre.takeUnless { it == manga.ogGenre },
                status = manga.status.takeUnless { it == manga.ogStatus }?.toLong(),
            )

            setCustomAnimeInfo.set(mangaInfo)
        }
        clearSelection()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncMangaToDex() {
        launchIO {
            MdUtil.getEnabledMangaDex(unsortedPreferences, sourcePreferences, sourceManager)?.let { mdex ->
                state.value.selection.fastFilter { it.anime.source in mangaDexSourceIds }.fastForEach { (manga) ->
                    mdex.updateFollowStatus(MdUtil.getMangaId(manga.url), FollowStatus.READING)
                }
            }
            clearSelection()
        }
    }

    fun resetInfo() {
        state.value.selection.fastForEach { (manga) ->
            val mangaInfo = CustomAnimeInfo(
                id = manga.id,
                title = null,
                author = null,
                artist = null,
                thumbnailUrl = null,
                description = null,
                genre = null,
                status = null,
            )

            setCustomAnimeInfo.set(mangaInfo)
        }
        clearSelection()
    }
    // SY <--

    /**
     * Marks mangas' episodes read status.
     */
    fun markReadSelection(read: Boolean) {
        val mangas = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                setSeenStatus.await(
                    anime = manga.anime,
                    seen = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded episodes.
     */
    fun removeMangas(mangaList: List<Anime>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        if (source is MergedSource) {
                            val mergedMangas = getMergedAnimeById.await(manga.id)
                            val sources = mergedMangas.distinctBy {
                                it.source
                            }.map { sourceManager.getOrStub(it.source) }
                            mergedMangas.forEach merge@{ mergedManga ->
                                val mergedSource =
                                    sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                                downloadManager.deleteManga(mergedManga, mergedSource)
                            }
                        } else {
                            downloadManager.deleteManga(manga, source)
                        }
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Anime>, addCategories: List<Long>, removeCategories: List<Long>) {
        screenModelScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAnimeCategories.await(manga.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns() else libraryPreferences.portraitColumns())
            .asState(screenModelScope)
    }

    suspend fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        if (state.value.categories.isEmpty()) return null

        return withIOContext {
            state.value
                .getLibraryItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    // SY -->
    fun getCategoryName(
        context: Context,
        category: Category?,
        groupType: Int,
        categoryName: String,
    ): String {
        return when (groupType) {
            LibraryGroup.BY_STATUS -> when (category?.id) {
                SAnime.ONGOING.toLong() -> context.stringResource(MR.strings.ongoing)
                SAnime.LICENSED.toLong() -> context.stringResource(MR.strings.licensed)
                SAnime.CANCELLED.toLong() -> context.stringResource(MR.strings.cancelled)
                SAnime.ON_HIATUS.toLong() -> context.stringResource(MR.strings.on_hiatus)
                SAnime.PUBLISHING_FINISHED.toLong() -> context.stringResource(MR.strings.publishing_finished)
                SAnime.COMPLETED.toLong() -> context.stringResource(MR.strings.completed)
                else -> context.stringResource(MR.strings.unknown)
            }
            LibraryGroup.BY_SOURCE -> if (category?.id == LocalSource.ID) {
                context.stringResource(MR.strings.local_source)
            } else {
                categoryName
            }
            LibraryGroup.BY_TRACK_STATUS ->
                TrackStatus.entries
                    .find { it.int.toLong() == category?.id }
                    .let { it ?: TrackStatus.OTHER }
                    .let { context.stringResource(it.res) }
            LibraryGroup.UNGROUPED -> context.stringResource(SYMR.strings.ungrouped)
            else -> categoryName
        }
    }

    suspend fun filterLibrary(unfiltered: List<LibraryItem>, query: String?, loggedInTrackServices: Map<Long, TriState>): List<LibraryItem> {
        return if (unfiltered.isNotEmpty() && !query.isNullOrBlank()) {
            // Prepare filter object
            val parsedQuery = searchEngine.parseQuery(query)
            val mangaWithMetaIds = getIdsOfFavoriteAnimeWithMetadata.await()
            val tracks = if (loggedInTrackServices.isNotEmpty()) {
                getTracks.await().groupBy { it.animeId }
            } else {
                emptyMap()
            }
            val sources = unfiltered
                .distinctBy { it.libraryAnime.anime.source }
                .fastMapNotNull { sourceManager.get(it.libraryAnime.anime.source) }
                .associateBy { it.id }
            unfiltered.asFlow().cancellable().filter { item ->
                val mangaId = item.libraryAnime.anime.id
                val sourceId = item.libraryAnime.anime.source
                if (isMetadataSource(sourceId)) {
                    if (mangaWithMetaIds.binarySearch(mangaId) < 0) {
                        // No meta? Filter using title
                        filterManga(
                            queries = parsedQuery,
                            libraryAnime = item.libraryAnime,
                            tracks = tracks[mangaId],
                            source = sources[sourceId],
                            loggedInTrackServices = loggedInTrackServices,
                        )
                    } else {
                        val tags = getSearchTags.await(mangaId)
                        val titles = getSearchTitles.await(mangaId)
                        filterManga(
                            queries = parsedQuery,
                            libraryAnime = item.libraryAnime,
                            tracks = tracks[mangaId],
                            source = sources[sourceId],
                            checkGenre = false,
                            searchTags = tags,
                            searchTitles = titles,
                            loggedInTrackServices = loggedInTrackServices,
                        )
                    }
                } else {
                    filterManga(
                        queries = parsedQuery,
                        libraryAnime = item.libraryAnime,
                        tracks = tracks[mangaId],
                        source = sources[sourceId],
                        loggedInTrackServices = loggedInTrackServices,
                    )
                }
            }.toList()
        } else {
            unfiltered
        }
    }

    private fun filterManga(
        queries: List<QueryComponent>,
        libraryAnime: LibraryAnime,
        tracks: List<Track>?,
        source: Source?,
        checkGenre: Boolean = true,
        searchTags: List<SearchTag>? = null,
        searchTitles: List<SearchTitle>? = null,
        loggedInTrackServices: Map<Long, TriState>,
    ): Boolean {
        val manga = libraryAnime.anime
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
                                    filterTracks(query, tracks, context)
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
                                            !filterTracks(query, tracks, context)
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

    private fun filterTracks(constraint: String, tracks: List<Track>, context: Context): Boolean {
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

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(manga: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == manga.id }) {
                    list.removeAll { it.id == manga.id }
                } else {
                    list.add(manga)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(manga: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != manga.category) {
                    list.add(manga)
                    return@mutate
                }

                val items = state.getLibraryItemsByCategoryId(manga.category)
                    ?.fastMap { it.libraryAnime }.orEmpty()
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> IntRange(lastMangaIndex, curMangaIndex)
                    curMangaIndex < lastMangaIndex -> IntRange(curMangaIndex, lastMangaIndex)
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getLibraryItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryAnime.takeUnless { it.id in selectedIds }
                    }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories[index].id
                val items = state.getLibraryItemsByCategoryId(categoryId)?.fastMap { it.libraryAnime }.orEmpty()
                val selectedIds = list.fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected manga
            val mangaList = state.value.selection.map { it.anime }

            // Hide the default category because it has a different behavior than the ones from db.
            // SY -->
            val categories = state.value.ogCategories.filter { it.id != 0L }
            // SY <--

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        val mangaList = state.value.selection.map { it.anime }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Anime>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Anime>) : Dialog
        data object SyncFavoritesWarning : Dialog
        data object SyncFavoritesConfirm : Dialog
    }

    // SY -->
    /** Returns first unread episode of a manga */
    suspend fun getFirstUnread(manga: Anime): Episode? {
        return getNextEpisodes.await(manga.id).firstOrNull()
    }

    private fun getGroupedMangaItems(
        groupType: Int,
        libraryManga: List<LibraryItem>,
    ): LibraryMap {
        val context = preferences.context
        return when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = runBlocking { getTracks.await() }.groupBy { it.animeId }
                libraryManga.groupBy { item ->
                    val status = tracks[item.libraryAnime.anime.id]?.firstNotNullOfOrNull { track ->
                        TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                    } ?: TrackStatus.OTHER

                    status.int
                }.mapKeys { (id) ->
                    Category(
                        id = id.toLong(),
                        name = TrackStatus.entries
                            .find { it.int == id }
                            .let { it ?: TrackStatus.OTHER }
                            .let { context.stringResource(it.res) },
                        order = TrackStatus.entries.indexOfFirst {
                            it.int == id
                        }.takeUnless { it == -1 }?.toLong() ?: TrackStatus.OTHER.ordinal.toLong(),
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                }
            }
            LibraryGroup.BY_SOURCE -> {
                val sources: List<Long>
                libraryManga.groupBy { item ->
                    item.libraryAnime.anime.source
                }.also {
                    sources = it.keys
                        .map {
                            sourceManager.getOrStub(it)
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id.toString() } })
                        .map { it.id }
                }.mapKeys {
                    Category(
                        id = it.key,
                        name = if (it.key == LocalSource.ID) {
                            context.stringResource(MR.strings.local_source)
                        } else {
                            val source = sourceManager.getOrStub(it.key)
                            source.name.ifBlank { source.id.toString() }
                        },
                        order = sources.indexOf(it.key).takeUnless { it == -1 }?.toLong() ?: Long.MAX_VALUE,
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                }
            }
            else -> {
                libraryManga.groupBy { item ->
                    item.libraryAnime.anime.status
                }.mapKeys {
                    Category(
                        id = it.key + 1,
                        name = when (it.key) {
                            SAnime.ONGOING.toLong() -> context.stringResource(MR.strings.ongoing)
                            SAnime.LICENSED.toLong() -> context.stringResource(MR.strings.licensed)
                            SAnime.CANCELLED.toLong() -> context.stringResource(MR.strings.cancelled)
                            SAnime.ON_HIATUS.toLong() -> context.stringResource(MR.strings.on_hiatus)
                            SAnime.PUBLISHING_FINISHED.toLong() -> context.stringResource(MR.strings.publishing_finished)
                            SAnime.COMPLETED.toLong() -> context.stringResource(MR.strings.completed)
                            else -> context.stringResource(MR.strings.unknown)
                        },
                        order = when (it.key) {
                            SAnime.ONGOING.toLong() -> 1
                            SAnime.LICENSED.toLong() -> 2
                            SAnime.CANCELLED.toLong() -> 3
                            SAnime.ON_HIATUS.toLong() -> 4
                            SAnime.PUBLISHING_FINISHED.toLong() -> 5
                            SAnime.COMPLETED.toLong() -> 6
                            else -> 7
                        },
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                }
            }
        }.toSortedMap(compareBy { it.order })
    }

    fun openFavoritesSyncDialog() {
        mutableState.update {
            it.copy(
                dialog = if (unsortedPreferences.exhShowSyncIntro().get()) {
                    Dialog.SyncFavoritesWarning
                } else {
                    Dialog.SyncFavoritesConfirm
                },
            )
        }
    }
    // SY <--

    // KMK -->
    /**
     * Will get first merged manga in the list as target merging.
     * If there is no merged manga, then it will use the first one in list to create a new target.
     */
    suspend fun smartSearchMerge(selectedMangas: PersistentList<LibraryAnime>): Long? {
        val mergedManga = selectedMangas.firstOrNull { it.anime.source == MERGED_SOURCE_ID }?.let { listOf(it) }
            ?: emptyList()
        val mergingMangas = selectedMangas.filterNot { it.anime.source == MERGED_SOURCE_ID }
        val toMergeMangas = mergedManga + mergingMangas
        if (toMergeMangas.size <= 1) return null

        var mergingMangaId = toMergeMangas.first().anime.id
        for (manga in toMergeMangas.drop(1)) {
            mergingMangaId = smartSearchMerge.smartSearchMerge(manga.anime, mergingMangaId).id
        }
        return mergingMangaId
    }
    // KMK <--

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
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
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: LibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAnime> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        // SY -->
        val showSyncExh: Boolean = false,
        val isSyncEnabled: Boolean = false,
        val ogCategories: List<Category> = emptyList(),
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        // SY <--
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryAnime.anime.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        // SY -->
        val showCleanTitles: Boolean by lazy {
            selection.any {
                it.anime.isEhBasedAnime() ||
                    it.anime.source in nHentaiSourceIds
            }
        }

        val showAddToMangadex: Boolean by lazy {
            selection.any { it.anime.source in mangaDexSourceIds }
        }

        val showResetInfo: Boolean by lazy {
            selection.fastAny { (manga) ->
                manga.title != manga.ogTitle ||
                    manga.author != manga.ogAuthor ||
                    manga.artist != manga.ogArtist ||
                    manga.thumbnailUrl != manga.ogThumbnailUrl ||
                    manga.description != manga.ogDescription ||
                    manga.genre != manga.ogGenre ||
                    manga.status != manga.ogStatus
            }
        }
        // SY <--

        fun getLibraryItemsByCategoryId(categoryId: Long): List<LibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getLibraryItemsByPage(page: Int): List<LibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getMangaCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getMangaCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
