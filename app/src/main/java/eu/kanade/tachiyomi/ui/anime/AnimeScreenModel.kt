package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.palette.graphics.Palette
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.Image
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.anime.interactor.GetExcludedScanlators
import eu.kanade.domain.anime.interactor.GetPagePreviews
import eu.kanade.domain.anime.interactor.SetExcludedScanlators
import eu.kanade.domain.anime.interactor.SmartSearchMerge
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.PagePreview
import eu.kanade.domain.anime.model.chaptersFiltered
import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.domain.anime.model.toDomainAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.GetAvailableScanlators
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.TrackEpisode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.ThumbnailPreviewSource
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.anime.RelatedAnime.Companion.isLoading
import eu.kanade.tachiyomi.ui.anime.RelatedAnime.Companion.removeDuplicates
import eu.kanade.tachiyomi.ui.anime.RelatedAnime.Companion.sorted
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.DebugToggles
import exh.md.utils.FollowStatus
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedAnime
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.anime.interactor.DeleteMergeById
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodes
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.anime.interactor.GetFlatMetadataById
import tachiyomi.domain.anime.interactor.GetMergedAnimeById
import tachiyomi.domain.anime.interactor.GetMergedReferencesById
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.interactor.UpdateMergedSettings
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.model.MergeAnimeSettingsUpdate
import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.interactor.GetMergedEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.service.calculateEpisodeGap
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.floor
import androidx.compose.runtime.State as RuntimeState

class AnimeScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val mangaId: Long,
    // SY -->
    /** If it is opened from Source then it will auto expand the manga description */
    private val isFromSource: Boolean,
    private val smartSearched: Boolean,
    // SY <--
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    readerPreferences: ReaderPreferences = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    // KMK -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getAnimeWithEpisodes: GetAnimeWithEpisodes = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getMergedEpisodesByAnimeId: GetMergedEpisodesByAnimeId = Injekt.get(),
    private val getMergedAnimeById: GetMergedAnimeById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    // KMK -->
    private val smartSearchMerge: SmartSearchMerge = Injekt.get(),
    // KMK <--
    private val updateMergedSettings: UpdateMergedSettings = Injekt.get(),
    val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val deleteMergeById: DeleteMergeById = Injekt.get(),
    private val getFlatMetadata: GetFlatMetadataById = Injekt.get(),
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val setCustomAnimeInfo: SetCustomAnimeInfo = Injekt.get(),
    // SY <--
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val getAvailableScanlators: GetAvailableScanlators = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags = Injekt.get(),
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<AnimeScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    // KMK -->
    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation().asState(screenModelScope)
    val themeCoverBased = uiPreferences.themeCoverBased().get()
    // KMK <--

    val anime: Anime?
        get() = successState?.anime

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = anime?.favorite ?: false

    private val allChapters: List<EpisodeList.Item>?
        get() = successState?.episodes

    private val filteredChapters: List<EpisodeList.Item>?
        get() = successState?.processedEpisodes

    val chapterSwipeStartAction = libraryPreferences.swipeToEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeToStartAction().get()
    private var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)

    val isUpdateIntervalEnabled =
        LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateAnimeRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    // SY -->
    private data class CombineState(
        val manga: Anime,
        val episodes: List<Episode>,
        val flatMetadata: FlatMetadata?,
        val mergedData: MergedAnimeData? = null,
        val pagePreviewsState: PagePreviewState = PagePreviewState.Loading,
    ) {
        constructor(pair: Pair<Anime, List<Episode>>, flatMetadata: FlatMetadata?) :
            this(pair.first, pair.second, flatMetadata)
    }
    // SY <--

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            getAnimeWithEpisodes.subscribe(mangaId, applyScanlatorFilter = true).distinctUntilChanged()
                // SY -->
                .combine(
                    getMergedEpisodesByAnimeId.subscribe(mangaId, true, applyScanlatorFilter = true)
                        .distinctUntilChanged(),
                ) { (manga, chapters), mergedChapters ->
                    if (manga.source == MERGED_SOURCE_ID) {
                        manga to mergedChapters
                    } else {
                        manga to chapters
                    }
                }
                .onEach { (manga, chapters) ->
                    if (chapters.isNotEmpty() &&
                        manga.isEhBasedAnime() &&
                        DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled
                    ) {
                    }
                }
                .combine(
                    getFlatMetadata.subscribe(mangaId)
                        .distinctUntilChanged(),
                ) { pair, flatMetadata ->
                    CombineState(pair, flatMetadata)
                }
                .combine(
                    combine(
                        getMergedAnimeById.subscribe(mangaId)
                            .distinctUntilChanged(),
                        getMergedReferencesById.subscribe(mangaId)
                            .distinctUntilChanged(),
                    ) { manga, references ->
                        if (manga.isNotEmpty()) {
                            MergedAnimeData(
                                references,
                                manga.associateBy { it.id },
                                references.map { it.animeSourceId }.distinct()
                                    .map { sourceManager.getOrStub(it) },
                            )
                        } else {
                            null
                        }
                    },
                ) { state, mergedData ->
                    state.copy(mergedData = mergedData)
                }
                .combine(downloadCache.changes) { state, _ -> state }
                .combine(downloadManager.queueState) { state, _ -> state }
                // SY <--
                .flowWithLifecycle(lifecycle)
                .collectLatest { (manga, chapters /* SY --> */, flatMetadata, mergedData /* SY <-- */) ->
                    val chapterItems = chapters.toChapterListItems(manga /* SY --> */, mergedData /* SY <-- */)
                    updateSuccessState {
                        it.copy(
                            anime = manga,
                            episodes = chapterItems,
                            // SY -->
                            meta = raiseMetadata(flatMetadata, it.source),
                            mergedData = mergedData,
                            // SY <--
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            getExcludedScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators.toImmutableSet())
                    }
                }
        }

        screenModelScope.launchIO {
            getAvailableScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                // SY -->
                .combine(
                    state.map { (it as? State.Success)?.anime }
                        .distinctUntilChangedBy { it?.source }
                        .flatMapConcat {
                            if (it?.source == MERGED_SOURCE_ID) {
                                getAvailableScanlators.subscribeMerge(mangaId)
                            } else {
                                flowOf(emptySet())
                            }
                        },
                ) { mangaScanlators, mergeScanlators ->
                    mangaScanlators + mergeScanlators
                } // SY <--
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators.toImmutableSet())
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val manga = getAnimeWithEpisodes.awaitManga(mangaId)

            // SY -->
            val mergedData = getMergedReferencesById.await(mangaId).takeIf { it.isNotEmpty() }?.let { references ->
                MergedAnimeData(
                    references,
                    getMergedAnimeById.await(mangaId).associateBy { it.id },
                    references.map { it.animeSourceId }.distinct()
                        .map { sourceManager.getOrStub(it) },
                )
            }
            val chapters = (
                if (manga.source ==
                    MERGED_SOURCE_ID
                ) {
                    getMergedEpisodesByAnimeId.await(mangaId, applyScanlatorFilter = true)
                } else {
                    getAnimeWithEpisodes.awaitChapters(mangaId, applyScanlatorFilter = true)
                }
                )
                .toChapterListItems(manga, mergedData)
            val meta = getFlatMetadata.await(mangaId)
            // SY <--

            if (!manga.favorite) {
                setAnimeDefaultEpisodeFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                // SY -->
                val source = sourceManager.getOrStub(manga.source)
                // SY <--
                State.Success(
                    anime = manga,
                    source = source,
                    isFromSource = isFromSource,
                    episodes = chapters,
                    // SY -->
                    availableScanlators = if (manga.source == MERGED_SOURCE_ID) {
                        getAvailableScanlators.awaitMerge(mangaId)
                    } else {
                        getAvailableScanlators.await(mangaId)
                    }.toImmutableSet(),
                    // SY <--
                    excludedScanlators = getExcludedScanlators.await(mangaId).toImmutableSet(),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    // SY -->
                    showRecommendationsInOverflow = uiPreferences.recommendsInOverflow().get(),
                    showMergeInOverflow = uiPreferences.mergeInOverflow().get(),
                    showMergeWithAnother = smartSearched,
                    mergedData = mergedData,
                    meta = raiseMetadata(meta, source),
                    pagePreviewsState = if (source.getMainSource() is ThumbnailPreviewSource) {
                        getPagePreviews(manga, source)
                        PagePreviewState.Loading
                    } else {
                        PagePreviewState.Unused
                    },
                    alwaysShowWatchingProgress =
                    readerPreferences.preserveReadingPosition().get() && manga.isEhBasedAnime(),
                    previewsRowCount = uiPreferences.previewsRowCount().get(),
                    // SY <--
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Fetch info-episodes when needed
            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    // KMK -->
                    async { syncTrackers() },
                    // KMK <--
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
                // KMK -->
                launch { fetchRelatedMangasFromSource() }
                // KMK <--
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // KMK -->
    /**
     * Get the color of the manga cover by loading cover with ImageRequest directly from network.
     */
    fun setPaletteColor(model: Any) {
        if (model is ImageRequest && model.defined.sizeResolver != null) return

        val imageRequestBuilder = if (model is ImageRequest) {
            model.newBuilder()
        } else {
            ImageRequest.Builder(context).data(model)
        }
            .allowHardware(false)

        val generatePalette: (Image) -> Unit = { image ->
            val bitmap = image.asDrawable(context.resources).getBitmapOrNull()
            if (bitmap != null) {
                Palette.from(bitmap).generate {
                    screenModelScope.launchIO {
                        if (it == null) return@launchIO
                        val animeCover = when (model) {
                            is Anime -> model.asAnimeCover()
                            is AnimeCover -> model
                            else -> return@launchIO
                        }
                        if (animeCover.isAnimeFavorite) {
                            it.dominantSwatch?.let { swatch ->
                                animeCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                            }
                        }
                        val vibrantColor = it.getBestColor() ?: return@launchIO
                        animeCover.vibrantCoverColor = vibrantColor
                        updateSuccessState {
                            it.copy(seedColor = Color(vibrantColor))
                        }
                    }
                }
            }
        }

        context.imageLoader.enqueue(
            imageRequestBuilder
                .target(
                    onSuccess = generatePalette,
                    onError = {
                        // TODO: handle error
                        // val file = coverCache.getCoverFile(manga!!)
                        // if (file.exists()) {
                        //     file.delete()
                        //     setPaletteColor()
                        // }
                    },
                )
                .build(),
        )
    }

    private suspend fun syncTrackers() {
        if (!trackPreferences.autoSyncProgressFromTrackers().get()) return

        val refreshTracks = Injekt.get<RefreshTracks>()
        refreshTracks.await(mangaId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$mangaId for service ${track!!.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }
    // KMK <--

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                // KMK -->
                async { syncTrackers() },
                // KMK <--
                async { fetchMangaFromSource(manualFetch) },
                async { fetchChaptersFromSource(manualFetch) },
            )
            fetchFromSourceTasks.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // Manga info - start

    /**
     * Fetch manga information from source.
     */
    private suspend fun fetchMangaFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val networkManga = state.source.getAnimeDetails(state.anime.toSAnime())
                updateAnime.awaitUpdateFromSource(state.anime, networkManga, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    // SY -->
    private fun raiseMetadata(flatMetadata: FlatMetadata?, source: Source): RaisedSearchMetadata? {
        return if (flatMetadata != null) {
            val metaClass = source.getMainSource<MetadataSource<*, *>>()?.metaClass
            if (metaClass != null) flatMetadata.raise(metaClass) else null
        } else {
            null
        }
    }

    fun updateMangaInfo(
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) {
        val state = successState ?: return
        var manga = state.anime
        if (state.anime.isLocal()) {
            val newTitle = if (title.isNullOrBlank()) manga.url else title.trim()
            val newAuthor = author?.trimOrNull()
            val newArtist = artist?.trimOrNull()
            val newThumbnailUrl = thumbnailUrl?.trimOrNull()
            val newDesc = description?.trimOrNull()
            manga = manga.copy(
                ogTitle = newTitle,
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogThumbnailUrl = thumbnailUrl?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
                lastUpdate = manga.lastUpdate + 1,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateAnimeInfo(manga.toSAnime())
            screenModelScope.launchNonCancellable {
                updateAnime.await(
                    AnimeUpdate(
                        manga.id,
                        title = newTitle,
                        author = newAuthor,
                        artist = newArtist,
                        thumbnailUrl = newThumbnailUrl,
                        description = newDesc,
                        genre = tags,
                        status = status,
                    ),
                )
            }
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags != state.anime.ogGenre) {
                tags
            } else {
                null
            }
            setCustomAnimeInfo.set(
                CustomAnimeInfo(
                    state.anime.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    thumbnailUrl?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    status.takeUnless { it == state.anime.ogStatus },
                ),
            )
            manga = manga.copy(lastUpdate = manga.lastUpdate + 1)
        }

        updateSuccessState { successState ->
            successState.copy(anime = manga)
        }
    }

    // KMK -->
    @Composable
    fun getManga(initialManga: Anime): RuntimeState<Anime> {
        return produceState(initialValue = initialManga) {
            getAnime.subscribe(initialManga.url, initialManga.source)
                .flowWithLifecycle(lifecycle)
                .collectLatest { manga ->
                    value = manga
                        // KMK -->
                        ?: initialManga
                    // KMK <--
                }
        }
    }

    suspend fun smartSearchMerge(manga: Anime, originalMangaId: Long): Anime {
        return smartSearchMerge.smartSearchMerge(manga, originalMangaId)
    }
    // KMK <--

    fun updateMergeSettings(mergedAnimeReferences: List<MergedAnimeReference>) {
        screenModelScope.launchNonCancellable {
            if (mergedAnimeReferences.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    mergedAnimeReferences.map {
                        MergeAnimeSettingsUpdate(
                            id = it.id,
                            isInfoAnime = it.isInfoAnime,
                            getEpisodeUpdates = it.getEpisodeUpdates,
                            episodePriority = it.episodePriority,
                            downloadEpisodes = it.downloadEpisodes,
                            episodeSortMode = it.episodeSortMode,
                        )
                    },
                )
            }
        }
    }

    fun deleteMerge(reference: MergedAnimeReference) {
        screenModelScope.launchNonCancellable {
            deleteMergeById.await(reference.id)
        }
    }
    // SY <--

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                screenModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.delete_downloads_for_manga),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val manga = state.anime

            if (isFavorited) {
                // Remove from library
                if (updateAnime.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        updateAnime.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryAnime.await(manga).getOrNull(0)

                    if (duplicate != null) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicate)) }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateAnime.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateAnime.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> showChangeCategoryDialog()
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(manga, state.source)
            }
        }
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.anime ?: return
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    fun showSetFetchIntervalDialog() {
        val manga = successState?.anime ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Anime, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateAnime.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = animeRepository.getAnimeById(manga.id)
                updateSuccessState { it.copy(anime = updatedManga) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.anime ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        // SY -->
        if (state.source is MergedSource) {
            val mergedManga = state.mergedData?.anime?.map { it.value to sourceManager.getOrStub(it.value.source) }
            mergedManga?.forEach { (manga, source) ->
                downloadManager.deleteManga(manga, source)
            }
        } else {
            /* SY <-- */ downloadManager.deleteManga(state.anime, state.source)
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Anime): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Anime, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        // SY -->
        val isMergedSource = source is MergedSource
        val mergedIds = if (isMergedSource) successState?.mergedData?.anime?.keys.orEmpty() else emptySet()
        // SY <--
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter {
                    /* SY --> */ if (isMergedSource) {
                        it.anime.id in mergedIds
                    } else {
                        /* SY <-- */ it.anime.id ==
                            successState?.anime?.id
                    }
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter {
                    /* SY --> */ if (isMergedSource) {
                        it.anime.id in mergedIds
                    } else {
                        /* SY <-- */ it.anime.id ==
                            successState?.anime?.id
                    }
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.episodes.indexOfFirst { it.id == download.episode.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.episodes.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(episodes = newChapters)
        }
    }

    private fun List<Episode>.toChapterListItems(
        manga: Anime,
        // SY -->
        mergedData: MergedAnimeData?,
        // SY <--
    ): List<EpisodeList.Item> {
        val isLocal = manga.isLocal()
        // SY -->
        val isExhManga = manga.isEhBasedAnime()
        // SY <--
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }

            // SY -->
            @Suppress("NAME_SHADOWING")
            val manga = mergedData?.anime?.get(chapter.animeId) ?: manga
            val source = mergedData?.sources?.find { manga.source == it.id }?.takeIf { mergedData.sources.size > 2 }
            // SY <--
            val downloaded = if (manga.isLocal()) {
                true
            } else {
                downloadManager.isEpisodeDownloaded(
                    // SY -->
                    chapter.name,
                    chapter.scanlator,
                    manga.ogTitle,
                    manga.source,
                    // SY <--
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            EpisodeList.Item(
                episode = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
                // SY -->
                sourceName = source?.getNameForAnimeInfo(),
                showScanlator = !isExhManga,
                // SY <--
            )
        }
    }

    // SY -->
    private fun getPagePreviews(manga: Anime, source: Source) {
        screenModelScope.launchIO {
            when (val result = getPagePreviews.await(manga, source, 1)) {
                is GetPagePreviews.Result.Error -> updateSuccessState {
                    it.copy(pagePreviewsState = PagePreviewState.Error(result.error))
                }
                is GetPagePreviews.Result.Success -> updateSuccessState {
                    it.copy(pagePreviewsState = PagePreviewState.Success(result.pagePreviews))
                }
                GetPagePreviews.Result.Unused -> updateSuccessState {
                    it.copy(pagePreviewsState = PagePreviewState.Unused)
                }
            }
        }
    }
    // SY <--

    /**
     * Requests an updated list of episodes from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                // SY -->
                if (state.source !is MergedSource) {
                    // SY <--
                    val chapters = state.source.getEpisodeList(state.anime.toSAnime())

                    val newChapters = syncEpisodesWithSource.await(
                        chapters,
                        state.anime,
                        state.source,
                        manualFetch,
                    )

                    if (manualFetch) {
                        downloadNewChapters(newChapters)
                    }
                    // SY -->
                } else {
                    state.source.fetchEpisodesForMergedAnime(state.anime, manualFetch)
                }
                // SY <--
            }
        } catch (e: Throwable) {
            val message = if (e is NoResultsException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newManga = animeRepository.getAnimeById(mangaId)
            updateSuccessState { it.copy(anime = newManga, isRefreshingData = false) }
        }
    }

    // KMK -->
    /**
     * Set the fetching related mangas status.
     * @param state
     * - false: started & fetching
     * - true: finished
     */
    private fun setRelatedMangasFetchedStatus(state: Boolean) {
        updateSuccessState { it.copy(isRelatedMangasFetched = state) }
    }

    /**
     * Requests an list of related mangas from the source.
     */
    internal suspend fun fetchRelatedMangasFromSource(onDemand: Boolean = false, onFinish: (() -> Unit)? = null) {
        val expandRelatedMangas = uiPreferences.expandRelatedAnimes().get()
        if (!onDemand && !expandRelatedMangas || anime?.source == MERGED_SOURCE_ID) return

        // start fetching related mangas
        setRelatedMangasFetchedStatus(false)

        fun exceptionHandler(e: Throwable) {
            logcat(LogPriority.ERROR, e)
            val message = with(context) { e.formattedMessage }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
        val state = successState ?: return
        val relatedMangasEnabled = sourcePreferences.relatedAnimes().get()

        try {
            if (state.source !is StubSource && relatedMangasEnabled) {
                state.source.getRelatedAnimeList(state.anime.toSAnime(), { e -> exceptionHandler(e) }) { pair, _ ->
                    /* Push found related mangas into collection */
                    val relatedAnime = RelatedAnime.Success.fromPair(pair) { mangaList ->
                        mangaList.map {
                            // KMK -->
                            it.toDomainAnime(state.source.id)
                            // KMK <--
                        }
                    }

                    updateSuccessState { successState ->
                        val relatedMangaCollection =
                            successState.relatedAnimeCollection
                                ?.toMutableStateList()
                                ?.apply { add(relatedAnime) }
                                ?: listOf(relatedAnime)
                        successState.copy(relatedAnimeCollection = relatedMangaCollection)
                    }
                }
            }
        } catch (e: Exception) {
            exceptionHandler(e)
        } finally {
            if (onFinish != null) {
                onFinish()
            } else {
                setRelatedMangasFetchedStatus(true)
            }
        }
    }
    // KMK <--

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: EpisodeList.Item, swipeAction: LibraryPreferences.EpisodeSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: EpisodeList.Item,
        swipeAction: LibraryPreferences.EpisodeSwipeAction,
    ) {
        val chapter = chapterItem.episode
        when (swipeAction) {
            LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> {
                markEpisodesSeen(listOf(chapter), !chapter.seen)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.EpisodeSwipeAction.Download -> {
                val downloadAction: EpisodeDownloadAction = when (chapterItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> EpisodeDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> EpisodeDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> EpisodeDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.EpisodeSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread episode or null if everything is read.
     */
    fun getNextUnreadChapter(): Episode? {
        val successState = successState ?: return null
        return successState.episodes.getNextUnseen(successState.anime)
    }

    private fun getUnreadChapters(): List<Episode> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.seen && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.episode }
    }

    private fun getUnreadChaptersSorted(): List<Episode> {
        val manga = successState?.anime ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getEpisodeSort(manga))
            // SY -->
            .let {
                if (manga.isEhBasedAnime()) it.reversed() else it
            }
        // SY <--
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun startDownload(
        episodes: List<Episode>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = episodes.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(episodes)
            }

            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<EpisodeList.Item>,
        action: EpisodeDownloadAction,
    ) {
        when (action) {
            EpisodeDownloadAction.START -> {
                startDownload(items.map { it.episode }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            EpisodeDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.episode ?: return
                startDownload(listOf(chapter), true)
            }
            EpisodeDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            EpisodeDownloadAction.DELETE -> {
                deleteChapters(items.map { it.episode })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_EPISODE -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_EPISODES -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_EPISODES -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_EPISODES -> getUnreadChaptersSorted().take(25)
            DownloadAction.UNSEEN_EPISODES -> getUnreadChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: Episode) {
        val manga = successState?.anime ?: return
        val chapters = filteredChapters.orEmpty().map { it.episode }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markEpisodesSeen(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected episode list as seen/unseen.
     * @param episodes the list of selected episodes.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        toggleAllSelection(false)
        if (episodes.isEmpty()) return
        screenModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = episodes.toTypedArray(),
            )

            if (!seen || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            val tracks = getTracks.await(mangaId)
            val maxChapterNumber = episodes.maxOf { it.episodeNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastEpisodeSeen }

            if (!shouldPromptTrackingUpdate) return@launchIO
            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackEpisode.await(context, mangaId, maxChapterNumber)
                withUIContext {
                    context.toast(context.stringResource(MR.strings.trackers_updated_summary, maxChapterNumber.toInt()))
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.confirm_tracker_update, maxChapterNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackEpisode.await(context, mangaId, maxChapterNumber)
            }
        }
    }

    /**
     * Downloads the given list of episodes with the manager.
     * @param episodes the list of episodes to download.
     */
    private fun downloadChapters(episodes: List<Episode>) {
        // SY -->
        val state = successState ?: return
        if (state.source is MergedSource) {
            episodes.groupBy { it.animeId }.forEach { map ->
                val manga = state.mergedData?.anime?.get(map.key) ?: return@forEach
                downloadManager.downloadEpisodes(manga, map.value)
            }
        } else {
            // SY <--
            val manga = state.anime
            downloadManager.downloadEpisodes(manga, episodes)
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param episodes the list of episodes to bookmark.
     */
    fun bookmarkChapters(episodes: List<Episode>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            episodes
                .filterNot { it.bookmark == bookmarked }
                .map { EpisodeUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of episode.
     *
     * @param episodes the list of episodes to delete.
     */
    fun deleteChapters(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteEpisodes(
                        episodes,
                        state.anime,
                        state.source,
                        // KMK -->
                        ignoreCategoryExclusion = true,
                        // KMK <--
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val manga = successState?.anime ?: return@launchNonCancellable
            val chaptersToDownload = filterEpisodesForDownload.await(manga, episodes)

            if (chaptersToDownload.isNotEmpty() /* SY --> */ && !manga.isEhBasedAnime() /* SY <-- */) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread episodes or all episodes.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded episodes or all episodes.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked episodes or all episodes.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.anime ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setAnimeDefaultEpisodeFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.anime ?: return
        screenModelScope.launchNonCancellable {
            setAnimeDefaultEpisodeFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: EpisodeList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedEpisodes.toMutableList().apply {
                val selectedIndex = successState.processedEpisodes.indexOfFirst { it.id == item.episode.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.episodes.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.episodes.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val state = successState
        val manga = state?.anime ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(manga.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { mangaTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(source!!) ?: true }
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = mangaTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks to supportedTrackers
            }
                // SY -->
                .map { (tracks, supportedTrackers) ->
                    val supportedTrackerTracks = if (manga.source in mangaDexSourceIds ||
                        state.mergedData?.anime?.values.orEmpty().any {
                            it.source in mangaDexSourceIds
                        }
                    ) {
                        val mdTrack = supportedTrackers.firstOrNull { it is MdList }
                        when {
                            mdTrack == null -> {
                                tracks
                            }
                            // KMK: auto track MangaDex
                            mdTrack.id !in tracks.map { it.trackerId } -> {
                                tracks + createMdListTrack()
                            }
                            else -> tracks
                        }
                    } else {
                        tracks
                    }
                    supportedTrackerTracks
                        .filter {
                            it.trackerId != trackerManager.mdList.id ||
                                it.status != FollowStatus.UNFOLLOWED.long
                        }
                        .size to supportedTrackers.isNotEmpty()
                }
                // SY <--
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }
    }

    // SY -->
    private suspend fun createMdListTrack(): Track {
        val state = successState!!
        val mdManga = state.anime.takeIf { it.source in mangaDexSourceIds }
            ?: state.mergedData?.anime?.values?.find { it.source in mangaDexSourceIds }
            ?: throw IllegalArgumentException("Could not create initial track")
        val track = trackerManager.mdList.createInitialTracker(state.anime, mdManga)
            .toDomainTrack(false)!!
        insertTrack.await(track)
        /* KMK -->
        return TrackItem(
            getTracks.await(mangaId).first { it.trackerId == trackerManager.mdList.id },
             trackerManager.mdList,
         )
        KMK <-- */
        return getTracks.await(mangaId).first { it.trackerId == trackerManager.mdList.id }
    }
    // SY <--

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Anime,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val episodes: List<Episode>) : Dialog
        data class DuplicateManga(val manga: Anime, val duplicate: Anime) : Dialog

        /* SY -->
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
        SY <-- */
        data class SetFetchInterval(val manga: Anime) : Dialog

        // SY -->
        data class EditMangaInfo(val manga: Anime) : Dialog
        data class EditMergedSettings(val mergedData: MergedAnimeData) : Dialog
        // SY <--

        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(episodes: List<Episode>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(episodes)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    /* SY -->
    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(newManga = manga, oldManga = duplicate)) }
    } SY <-- */

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        screenModelScope.launchIO {
            setExcludedScanlators.await(mangaId, excludedScanlators)
        }
    }

    // SY -->
    fun showEditMangaInfoDialog() {
        mutableState.update { state ->
            when (state) {
                State.Loading -> state
                is State.Success -> {
                    state.copy(dialog = Dialog.EditMangaInfo(state.anime))
                }
            }
        }
    }

    fun showEditMergedSettingsDialog() {
        val mergedData = successState?.mergedData ?: return
        mutableState.update { state ->
            when (state) {
                State.Loading -> state
                is State.Success -> {
                    state.copy(dialog = Dialog.EditMergedSettings(mergedData))
                }
            }
        }
    }
    // SY <--

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val source: Source,
            val isFromSource: Boolean,
            val episodes: List<EpisodeList.Item>,
            val availableScanlators: ImmutableSet<String>,
            val excludedScanlators: ImmutableSet<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,

            // SY -->
            val meta: RaisedSearchMetadata?,
            val mergedData: MergedAnimeData?,
            val showRecommendationsInOverflow: Boolean,
            val showMergeInOverflow: Boolean,
            val showMergeWithAnother: Boolean,
            val pagePreviewsState: PagePreviewState,
            val alwaysShowWatchingProgress: Boolean,
            val previewsRowCount: Int,
            // SY <--
            // KMK -->
            /**
             * status of fetching related mangas
             * - null: not started
             * - false: started & fetching
             * - true: finished
             */
            val isRelatedMangasFetched: Boolean? = null,
            /**
             * a list of <keyword, related mangas>
             */
            val relatedAnimeCollection: List<RelatedAnime>? = null,
            val seedColor: Color? = anime.asAnimeCover().vibrantCoverColor?.let { Color(it) },
            // KMK <--
        ) : State {
            // KMK -->
            /**
             * a value of null will be treated as still loading, so if all searching were failed and won't update
             * 'relatedAnimeCollection` then we should return empty list
             */
            val relatedAnimesSorted = relatedAnimeCollection
                ?.sorted(anime)
                ?.removeDuplicates(anime)
                ?.filter { it.isVisible() }
                ?.isLoading(isRelatedMangasFetched)
                ?: if (isRelatedMangasFetched == true) emptyList() else null
            // KMK <--

            val processedEpisodes by lazy {
                episodes.applyFilters(anime).toList()
                    // KMK -->
                    // safe-guard some edge-cases where episodes are duplicated some how on a merged entry
                    .distinctBy { it.id }
                // KMK <--
            }

            val isAnySelected by lazy {
                episodes.fastAny { it.selected }
            }

            val episodeListItems by lazy {
                processedEpisodes.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (anime.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.episode.episodeNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateEpisodeGap(higherChapter.episode, lowerChapter.episode)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            EpisodeList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || anime.chaptersFiltered()

            /**
             * Applies the view filters to the list of episodes obtained from the database.
             * @return an observable of the list of episodes filtered and sorted.
             */
            private fun List<EpisodeList.Item>.applyFilters(manga: Anime): Sequence<EpisodeList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unseenFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.seen } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) -> getEpisodeSort(manga).invoke(chapter1, chapter2) }
            }
        }
    }
}

// SY -->
data class MergedAnimeData(
    val references: List<MergedAnimeReference>,
    val anime: Map<Long, Anime>,
    val sources: List<Source>,
)
// SY <--

@Immutable
sealed class EpisodeList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : EpisodeList()

    @Immutable
    data class Item(
        val episode: Episode,
        val downloadState: Download.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
        // SY -->
        val sourceName: String?,
        val showScanlator: Boolean,
        // SY <--
    ) : EpisodeList() {
        val id = episode.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
    }
}

// SY -->
sealed interface PagePreviewState {
    data object Unused : PagePreviewState
    data object Loading : PagePreviewState
    data class Success(val pagePreviews: List<PagePreview>) : PagePreviewState
    data class Error(val error: Throwable) : PagePreviewState
}
// SY <--

// KMK -->
sealed interface RelatedAnime {
    data object Loading : RelatedAnime

    data class Success(
        val keyword: String,
        val mangaList: List<Anime>,
    ) : RelatedAnime {
        val isEmpty: Boolean
            get() = mangaList.isEmpty()

        companion object {
            suspend fun fromPair(
                pair: Pair<String, List<SAnime>>,
                toManga: suspend (mangaList: List<SAnime>) -> List<Anime>,
            ) = Success(pair.first, toManga(pair.second))
        }
    }

    fun isVisible(): Boolean {
        return this is Loading || (this is Success && !this.isEmpty)
    }

    companion object {
        internal fun List<RelatedAnime>.sorted(manga: Anime): List<RelatedAnime> {
            val success = filterIsInstance<Success>()
            val loading = filterIsInstance<Loading>()
            val title = manga.title.lowercase()
            val ogTitle = manga.ogTitle.lowercase()
            return success.filter { it.keyword.isEmpty() } +
                success.filter { it.keyword.lowercase() == title } +
                success.filter { it.keyword.lowercase() == ogTitle && ogTitle != title } +
                success.filter { it.keyword.isNotEmpty() && it.keyword.lowercase() !in listOf(title, ogTitle) }
                    .sortedByDescending { it.keyword.length }
                    .sortedBy { it.mangaList.size } +
                loading
        }

        internal fun List<RelatedAnime>.removeDuplicates(manga: Anime): List<RelatedAnime> {
            val mangaHashes = HashSet<Int>().apply { add(manga.url.hashCode()) }

            return map { relatedManga ->
                if (relatedManga is Success) {
                    val stripedList = relatedManga.mangaList.mapNotNull {
                        if (!mangaHashes.contains(it.url.hashCode())) {
                            mangaHashes.add(it.url.hashCode())
                            it
                        } else {
                            null
                        }
                    }
                    Success(
                        relatedManga.keyword,
                        stripedList,
                    )
                } else {
                    relatedManga
                }
            }
        }

        internal fun List<RelatedAnime>.isLoading(isRelatedMangaFetched: Boolean?): List<RelatedAnime> {
            return if (isRelatedMangaFetched == false) this + listOf(Loading) else this
        }
    }
}
// KMK <--
