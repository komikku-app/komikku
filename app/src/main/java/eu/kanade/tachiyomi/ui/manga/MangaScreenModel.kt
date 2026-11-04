package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
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
import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.SmartSearchMerge
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.chaptersFiltered
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.comment.CommentProviderRegistry
import eu.kanade.tachiyomi.source.comment.CommentRequest
import eu.kanade.tachiyomi.source.comment.SourceComment
import eu.kanade.tachiyomi.source.mangafire.MangaFireMetadataOverlay
import eu.kanade.tachiyomi.source.mangafire.MangaFireOverlayMetadata
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.isLoading
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.removeDuplicates
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.sorted
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
// KMK -->
import eu.kanade.tachiyomi.util.chapter.removeDuplicates as removeDuplicateChapters
import eu.kanade.tachiyomi.util.chapter.hideNonDownloadedDuplicates
import eu.kanade.tachiyomi.util.chapter.removeSubChapterDuplicates
import eu.kanade.tachiyomi.util.chapter.unreadSkippedSubChapterDuplicates
// KMK <--
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.log.xLogD
import exh.md.utils.FollowStatus
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.source.MANGAFIRE_PACKAGE
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.ExcludeChapter
import tachiyomi.domain.chapter.interactor.ReorderChapter
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.floor
import androidx.compose.runtime.State as RuntimeState

class MangaScreenModel(
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
    private val refreshTracks: RefreshTracks = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    // KMK <--
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    // KMK -->
    private val smartSearchMerge: SmartSearchMerge = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val commentProviderRegistry: CommentProviderRegistry = Injekt.get(),
    // KMK <--
    private val updateMergedSettings: UpdateMergedSettings = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val deleteMergeById: DeleteMergeById = Injekt.get(),
    private val getFlatMetadata: GetFlatMetadataById = Injekt.get(),
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    // SY <--
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getAvailableScanlators: GetAvailableScanlators = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val filterChaptersForDownload: FilterChaptersForDownload = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    // KMK -->
    private val deleteLibraryUpdateErrors: DeleteLibraryUpdateErrors = Injekt.get(),
    private val insertLibraryUpdateErrors: InsertLibraryUpdateErrors = Injekt.get(),
    private val insertLibraryUpdateErrorMessages: InsertLibraryUpdateErrorMessages = Injekt.get(),
    private val deleteChaptersFromDb: DeleteChapters = Injekt.get(),
    private val reorderChapter: ReorderChapter = Injekt.get(),
    private val excludeChapter: ExcludeChapter = Injekt.get(),
    // KMK <--
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    // KMK -->
    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation().asState(screenModelScope)
    val themeCoverBased = uiPreferences.themeCoverBased().get()
    private var savedChapterOrder: List<Pair<Long, Long?>>? = null
    private var commentsJob: Job? = null
    // KMK <--

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeToEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeToStartAction().get()
    private var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)
    // KMK -->
    private val skipDupe by readerPreferences.skipDupe().asState(screenModelScope)
    private val orphanedChapterUrlPrefix = "orphaned://"
    // KMK <--

    val isUpdateIntervalEnabled =
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateMangaRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    internal var showTrackDialogAfterCategorySelection: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.hasLoggedInTrackers == true && trackPreferences.trackOnAddingToLibrary().get()

    // EXH -->
    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    val redirectFlow: MutableSharedFlow<EXHRedirect> = MutableSharedFlow()

    data class EXHRedirect(val mangaId: Long)
    // EXH <--

    // SY -->
    private data class CombineState(
        val manga: Manga,
        val chapters: List<Chapter>,
        val flatMetadata: FlatMetadata?,
        val mergedData: MergedMangaData? = null,
        val pagePreviewsState: PagePreviewState = PagePreviewState.Loading,
    ) {
        constructor(pair: Pair<Manga, List<Chapter>>, flatMetadata: FlatMetadata?) :
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
            getMangaAndChapters.subscribe(mangaId, applyFilter = true).distinctUntilChanged()
                // SY -->
                .combine(
                    getMergedChaptersByMangaId.subscribe(mangaId, true, applyFilter = true)
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
                        manga.isEhBasedManga() &&
                        DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled
                    ) {
                        // Check for gallery in library and accept manga with lowest id
                        // Find chapters sharing same root
                        launchIO {
                            try {
                                val (acceptedChain) = updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)
                                // Redirect if we are not the accepted root
                                if (manga.id != acceptedChain.manga.id && acceptedChain.manga.favorite) {
                                    // Update if any of our chapters are not in accepted manga's chapters
                                    xLogD("Found accepted manga %s", manga.url)
                                    redirectFlow.emit(
                                        EXHRedirect(acceptedChain.manga.id),
                                    )
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Error loading accepted chapter chain" }
                            }
                        }
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
                        getMergedMangaById.subscribe(mangaId)
                            .distinctUntilChanged(),
                        getMergedReferencesById.subscribe(mangaId)
                            .distinctUntilChanged(),
                    ) { manga, references ->
                        if (manga.isNotEmpty()) {
                            MergedMangaData(
                                references,
                                manga.associateBy { it.id },
                                references.map { it.mangaSourceId }.distinct()
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
                            manga = manga,
                            chapters = chapterItems,
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
                    state.map { (it as? State.Success)?.manga }
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
            val manga = getMangaAndChapters.awaitManga(mangaId)

            // SY -->
            val mergedData = getMergedReferencesById.await(mangaId).takeIf { it.isNotEmpty() }?.let { references ->
                MergedMangaData(
                    references,
                    getMergedMangaById.await(mangaId).associateBy { it.id },
                    references.map { it.mangaSourceId }.distinct()
                        .map { sourceManager.getOrStub(it) },
                )
            }
            val chapters = if (manga.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(mangaId, applyFilter = true)
            } else {
                getMangaAndChapters.awaitChapters(mangaId, applyFilter = true)
            }
                .toChapterListItems(manga, mergedData)
            val meta = getFlatMetadata.await(mangaId)
            // SY <--

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                // SY -->
                val source = sourceManager.getOrStub(manga.source)
                // SY <--
                State.Success(
                    manga = manga,
                    source = source,
                    isFromSource = isFromSource,
                    chapters = chapters,
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
                    hideMissingChapters = libraryPreferences.hideMissingChapters().get(),
                    // SY -->
                    showRecommendationsInOverflow = uiPreferences.recommendsInOverflow().get(),
                    showMergeInOverflow = uiPreferences.mergeInOverflow().get(),
                    showMergeWithAnother = smartSearched,
                    mergedData = mergedData,
                    meta = raiseMetadata(meta, source),
                    pagePreviewsState = if (source.getMainSource() is PagePreviewSource) {
                        getPagePreviews(manga, source)
                        PagePreviewState.Loading
                    } else {
                        PagePreviewState.Unused
                    },
                    alwaysShowReadingProgress =
                    readerPreferences.preserveReadingPosition().get() && manga.isEhBasedManga(),
                    previewsRowCount = uiPreferences.previewsRowCount().get(),
                    // SY <--
                    // KMK -->
                    skipDupeEnabled = readerPreferences.skipDupe().get(),
                    // KMK <--
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Fetch info-chapters when needed
            if (screenModelScope.isActive) {
                // KMK -->
                val fetchFromSourceTasks = listOf(
                    async { syncTrackers() },
                    async {
                        if (needRefreshInfo || needRefreshChapter) {
                            // KMK <--
                            fetchAllFromSource(
                                manualFetch = false,
                                fetchDetails = needRefreshInfo,
                                fetchChapters = needRefreshChapter,
                            )
                            // KMK -->
                        }
                    },
                )
                fetchFromSourceTasks.awaitAll()
                launch { fetchRelatedMangasFromSource() }
                launchIO { enrichMangaFireOverlay() }
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
                        val mangaCover = when (model) {
                            is Manga -> model.asMangaCover()
                            is MangaCover -> model
                            else -> return@launchIO
                        }
                        if (mangaCover.isMangaFavorite) {
                            it.dominantSwatch?.let { swatch ->
                                mangaCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                            }
                        }
                        val vibrantColor = it.getBestColor() ?: return@launchIO
                        mangaCover.vibrantCoverColor = vibrantColor
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

        refreshTracks.await(mangaId, enhancedTrackersOnly = false)
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
            // KMK -->
            val fetchFromSourceTasks = listOf(
                async { syncTrackers() },
                async {
                    // KMK <--
                    fetchAllFromSource(
                        manualFetch = manualFetch,
                        fetchDetails = true,
                        fetchChapters = true,
                    )
                },
            )
            // KMK -->
            fetchFromSourceTasks.awaitAll()
            // KMK <--
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    private suspend fun fetchAllFromSource(
        manualFetch: Boolean,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) {
        val state = successState ?: return
        try {
            withIOContext {
                val update = updateMangaFromRemote(
                    source = state.source,
                    manga = state.manga,
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                    manualFetch = manualFetch,
                )
                    .getOrThrow()

                // KMK -->
                clearErrorFromDB(state.manga.id)
                // KMK <--

                if (manualFetch) {
                    downloadNewChapters(update.newChapters)
                }
            }
            if (fetchDetails) {
                enrichMangaFireOverlay()
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (e: Exception) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            // KMK -->
            writeErrorToDB(state.manga to with(context) { e.formattedMessage })
            // KMK <--
        }
    }

    // KMK -->
    /**
     * Soft-fail MangaFire stats overlay. Prefer extension [HttpSource.client] for VRF/CF.
     * Does not block manga details; row hides when overlay is null/empty.
     */
    private suspend fun enrichMangaFireOverlay() {
        val state = successState ?: return
        if (extensionManager.getExtensionPackage(state.source.id) != MANGAFIRE_PACKAGE) return
        val httpSource = state.source.getMainSource<HttpSource>() ?: return
        val overlay = withIOContext {
            runCatching {
                MangaFireMetadataOverlay.enrich(
                    mangaId = state.manga.id,
                    mangaUrl = state.manga.url,
                    baseUrl = httpSource.baseUrl,
                    client = httpSource.client,
                    headers = httpSource.headers.newBuilder()
                        .set("Accept", "application/json")
                        .build(),
                )
            }.getOrNull()?.takeUnless { it.isEmpty }
        } ?: return
        updateSuccessState { it.copy(mangaFireOverlay = overlay) }
    }

    /** True when a [CommentProvider] is registered for this source (CTA visibility for 1.4b). */
    fun isCommentsSupported(): Boolean {
        val state = successState ?: return false
        val packageName = extensionManager.getExtensionPackage(state.source.id)
        return commentProviderRegistry.exists(state.source.id, packageName)
    }

    /**
     * Badge count when known: last loaded total, else MangaDex meta. Null hides the badge but the
     * CTA may still show (count 0 with an existing thread opens the empty state).
     */
    fun commentsBadgeCount(): Int? {
        val state = successState ?: return null
        state.commentsTotalCount?.let { return it }
        (state.meta as? MangaDexSearchMetadata)?.commentCount?.let { return it }
        return null
    }

    /** Providers never touch the DB; everything they may need is resolved here. */
    private fun buildCommentRequest(state: State.Success): CommentRequest {
        val mangaDexMeta = state.meta as? MangaDexSearchMetadata
        val chapters = state.chapters.map { it.chapter }
        return CommentRequest(
            mangaId = state.manga.id,
            sourceId = state.source.id,
            manga = state.manga.toSManga(),
            latestChapter = latestChapter(chapters)?.toSChapter(),
            recentChapters = recentChaptersForComments(chapters).map { it.toSChapter() },
            threadId = mangaDexMeta?.commentThreadId,
            knownCount = mangaDexMeta?.commentCount,
        )
    }

    /** Newest chapter: highest recognized number, else first in source order. */
    private fun latestChapter(chapters: List<Chapter>): Chapter? =
        chapters.filter { it.isRecognizedNumber }.maxByOrNull { it.chapterNumber }
            ?: chapters.minByOrNull { it.sourceOrder }

    /**
     * Newest-first chapters for CopyManga roast aggregation.
     * Provider loads a small batch first; Load more walks further down this list.
     */
    private fun recentChaptersForComments(chapters: List<Chapter>): List<Chapter> {
        val recognized = chapters.filter { it.isRecognizedNumber }.sortedByDescending { it.chapterNumber }
        val ordered = recognized.ifEmpty { chapters.sortedByDescending { it.sourceOrder } }
        return ordered.take(60)
    }

    fun showCommentsSheet() {
        if (!isCommentsSupported()) return
        updateSuccessState {
            it.copy(dialog = Dialog.CommentsSheet, commentsState = CommentsState.Loading)
        }
        fetchComments(page = 1, append = false)
    }

    fun loadMoreComments() {
        val loaded = successState?.commentsState as? CommentsState.Loaded ?: return
        if (!loaded.hasNextPage || loaded.isLoadingMore) return
        updateSuccessState {
            val current = it.commentsState as? CommentsState.Loaded ?: return@updateSuccessState it
            it.copy(commentsState = current.copy(isLoadingMore = true))
        }
        fetchComments(page = loaded.page + 1, append = true)
    }

    private fun fetchComments(page: Int, append: Boolean) {
        commentsJob?.cancel()
        commentsJob = screenModelScope.launchIO {
            val state = successState ?: return@launchIO
            val packageName = extensionManager.getExtensionPackage(state.source.id)
            val provider = commentProviderRegistry.get(state.source.id, packageName)
            if (provider == null) {
                updateSuccessState {
                    it.copy(
                        commentsState = CommentsState.Error(
                            message = context.stringResource(KMR.strings.information_comments_unavailable),
                        ),
                    )
                }
                return@launchIO
            }
            try {
                val result = provider.loadPage(buildCommentRequest(state), page)
                updateSuccessState { success ->
                    val previous = if (append) {
                        (success.commentsState as? CommentsState.Loaded)?.comments.orEmpty()
                    } else {
                        emptyList()
                    }
                    // Offset-based sources can repeat an item when new comments shift pages
                    val merged = (previous + result.comments).distinctBy { it.id }
                    success.copy(
                        commentsTotalCount = result.totalCount ?: success.commentsTotalCount,
                        commentsState = if (merged.isEmpty() && !result.hasNextPage) {
                            CommentsState.Empty(subtitle = result.subtitle, totalCount = result.totalCount)
                        } else {
                            CommentsState.Loaded(
                                comments = merged,
                                page = result.page,
                                hasNextPage = result.hasNextPage,
                                totalCount = result.totalCount,
                                subtitle = result.subtitle,
                                isLoadingMore = false,
                            )
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (append) {
                    updateSuccessState {
                        val current = it.commentsState as? CommentsState.Loaded ?: return@updateSuccessState it
                        it.copy(commentsState = current.copy(isLoadingMore = false))
                    }
                } else {
                    updateSuccessState {
                        it.copy(
                            commentsState = CommentsState.Error(
                                message = e.message?.takeIf { msg -> msg.isNotBlank() }
                                    ?: context.stringResource(MR.strings.unknown_error),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun cancelCommentsFetch() {
        commentsJob?.cancel()
        commentsJob = null
        updateSuccessState { it.copy(commentsState = CommentsState.Idle) }
    }

    private suspend fun clearErrorFromDB(mangaId: Long) {
        deleteLibraryUpdateErrors.deleteMangaError(mangaIds = listOf(mangaId))
    }

    private suspend fun writeErrorToDB(error: Pair<Manga, String?>) {
        val errorMessage = error.second ?: context.stringResource(MR.strings.unknown_error)
        val errorMessageId = insertLibraryUpdateErrorMessages.insert(
            libraryUpdateErrorMessage = LibraryUpdateErrorMessage(-1L, errorMessage),
        )

        insertLibraryUpdateErrors.upsert(
            LibraryUpdateError(id = -1L, mangaId = error.first.id, messageId = errorMessageId),
        )
    }
    // KMK <--

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
        var manga = state.manga
        if (state.manga.isLocal()) {
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
            (sourceManager.get(LocalSource.ID) as LocalSource).updateMangaInfo(manga.toSManga())
            screenModelScope.launchNonCancellable {
                updateManga.await(
                    MangaUpdate(
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
            val genre = if (!tags.isNullOrEmpty() && tags != state.manga.ogGenre) {
                tags
            } else {
                null
            }
            setCustomMangaInfo.set(
                CustomMangaInfo(
                    state.manga.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    thumbnailUrl?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    status.takeUnless { it == state.manga.ogStatus },
                ),
            )
            manga = manga.copy(lastUpdate = manga.lastUpdate + 1)
        }

        updateSuccessState { successState ->
            successState.copy(manga = manga)
        }
    }

    // KMK -->
    @Composable
    fun getManga(initialManga: Manga): RuntimeState<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .flowWithLifecycle(lifecycle)
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        return smartSearchMerge.smartSearchMerge(manga, originalMangaId)
    }
    // KMK <--

    fun updateMergeSettings(mergedMangaReferences: List<MergedMangaReference>) {
        screenModelScope.launchNonCancellable {
            if (mergedMangaReferences.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    mergedMangaReferences.map {
                        MergeMangaSettingsUpdate(
                            id = it.id,
                            isInfoManga = it.isInfoManga,
                            getChapterUpdates = it.getChapterUpdates,
                            chapterPriority = it.chapterPriority,
                            downloadChapters = it.downloadChapters,
                            chapterSortMode = it.chapterSortMode,
                        )
                    },
                )
            }
        }
    }

    fun deleteMerge(reference: MergedMangaReference) {
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
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicates = getDuplicateLibraryManga(manga)

                    if (duplicates.isNotEmpty()) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
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
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> {
                        showTrackDialogAfterCategorySelection = true
                        showChangeCategoryDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(manga, state.source)
                if (autoOpenTrack && !showTrackDialogAfterCategorySelection) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.manga ?: return
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
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedManga) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        // SY -->
        if (state.source is MergedSource) {
            val mergedManga = state.mergedData?.manga?.map { it.value to sourceManager.getOrStub(it.value.source) }
            mergedManga?.forEach { (manga, source) ->
                downloadManager.deleteManga(manga, source)
            }
        } else {
            /* SY <-- */ downloadManager.deleteManga(state.manga, state.source)
        }
    }

    /**
     * Opens manga folder with the system's file manager.
     */
    fun openMangaFolder(currentSource: Source?, currentManga: Manga?) {
        try {
            if (currentManga == null || currentSource == null || currentSource is StubSource) return

            val mangaDir = downloadProvider.findMangaDir(/* SY --> */ currentManga.ogTitle /* SY <-- */, currentSource) ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mangaDir.uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            context.toast(e.message ?: context.stringResource(KMR.strings.error_opening_folder))
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
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
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
            setMangaCategories.await(mangaId, categoryIds)
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
        val mergedIds = if (isMergedSource) successState?.mergedData?.manga?.keys.orEmpty() else emptySet()
        // SY <--
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter {
                    /* SY --> */ if (isMergedSource) {
                        it.manga.id in mergedIds
                    } else {
                        /* SY <-- */ it.manga.id ==
                            successState?.manga?.id
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
                        it.manga.id in mergedIds
                    } else {
                        /* SY <-- */ it.manga.id ==
                            successState?.manga?.id
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
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun List<Chapter>.toChapterListItems(
        manga: Manga,
        // SY -->
        mergedData: MergedMangaData?,
        // SY <--
    ): List<ChapterList.Item> {
        val isLocal = manga.isLocal()
        // SY -->
        val isExhManga = manga.isEhBasedManga()
        // SY <--
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }

            // SY -->
            @Suppress("NAME_SHADOWING")
            val manga = mergedData?.manga?.get(chapter.mangaId) ?: manga
            val source = mergedData?.sources?.find { manga.source == it.id }?.takeIf { mergedData.sources.size > 2 }
            // SY <--
            val downloaded = if (manga.isLocal()) {
                true
            } else {
                downloadManager.isChapterDownloaded(
                    chapter,
                    manga.ogTitle,
                    manga.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
                // SY -->
                sourceName = source?.getNameForMangaInfo(),
                showScanlator = !isExhManga,
                // SY <--
            )
        }
    }

    // SY -->
    private fun getPagePreviews(manga: Manga, source: Source) {
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
        val expandRelatedMangas = uiPreferences.expandRelatedMangas().get()
        if ((!onDemand && !expandRelatedMangas) || manga?.source == MERGED_SOURCE_ID) return

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
        val relatedMangasEnabled = sourcePreferences.relatedMangas().get()

        try {
            if (state.source !is StubSource && relatedMangasEnabled) {
                state.source.getRelatedMangaList(state.manga.toSManga(), { e -> exceptionHandler(e) }) { pair, _ ->
                    /* Push found related mangas into collection */
                    val relatedManga = RelatedManga.Success.fromPair(pair) { mangaList ->
                        mangaList
                            .map { it.toDomainManga(state.source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it, false) }
                    }

                    updateSuccessState { successState ->
                        val relatedMangaCollection =
                            successState.relatedMangaCollection
                                ?.toMutableStateList()
                                ?.apply { add(relatedManga) }
                                ?: listOf(relatedManga)
                        successState.copy(relatedMangaCollection = relatedMangaCollection)
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
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
            // SY -->
            .let {
                if (manga.isEhBasedManga()) it.reversed() else it
            }
        // SY <--
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(chapters)
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
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_CHAPTERS -> getUnreadChaptersSorted().take(25)
            DownloadAction.UNREAD_CHAPTERS -> getUnreadChapters()
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

    fun markPreviousChapterRead(pointer: Chapter) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        screenModelScope.launchIO {
            setReadStatus.await(
                manga = successState?.manga ?: return@launchIO,
                read = read,
                chapters = chapters.toTypedArray(),
            )

            if (!read || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(mangaId)
            val maxChapterNumber = chapters.maxOf { it.chapterNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }

            if (!shouldPromptTrackingUpdate) return@launchIO
            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackChapter.await(context, mangaId, maxChapterNumber)
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
                trackChapter.await(context, mangaId, maxChapterNumber)
            }
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshTracks = Injekt.get(),
    ) {
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

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<Chapter>) {
        // KMK -->
        val state = successState ?: return
        val dedupedChapters = if (skipDupe) {
            val (orphaned, normal) = chapters.partition { it.url.startsWith(orphanedChapterUrlPrefix) }
            val effectiveChapters = normal.removeDuplicateChapters() + orphaned
            effectiveChapters.let {
                if (state.manga.skipSubChapterDuplicates) {
                    it.removeSubChapterDuplicates()
                } else {
                    it
                }
            }
        } else {
            chapters
        }
        // KMK <--
        // SY -->
        if (state.source is MergedSource) {
            dedupedChapters.groupBy { it.mangaId }.forEach { map ->
                val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                downloadManager.downloadChapters(manga, map.value)
            }
        } else {
            // SY <--
            val manga = state.manga
            downloadManager.downloadChapters(manga, dedupedChapters)
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    // KMK --?
                    if (state.source.id == MERGED_SOURCE_ID) {
                        chapters.groupBy { it.mangaId }.forEach { map ->
                            val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                            val source = state.mergedData.sources.find { it.id != MERGED_SOURCE_ID && manga.source == it.id } ?: return@forEach
                            downloadManager.deleteChapters(
                                map.value,
                                manga,
                                source,
                                ignoreCategoryExclusion = true,
                            )
                            if (source.isLocal()) {
                                // Refresh chapters state for Local source
                                fetchAllFromSource(
                                    manualFetch = false,
                                    fetchDetails = false,
                                    fetchChapters = true,
                                )
                            }
                        }
                    } else {
                        // KMK <--
                        downloadManager.deleteChapters(
                            chapters,
                            state.manga,
                            state.source,
                            // KMK -->
                            ignoreCategoryExclusion = true,
                            // KMK <--
                        )
                        // KMK -->
                        if (state.source.isLocal()) {
                            // Refresh chapters state for Local source
                            fetchAllFromSource(
                                manualFetch = false,
                                fetchDetails = false,
                                fetchChapters = true,
                            )
                        }
                        // KMK <--
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // KMK -->
    fun excludeChapters(items: List<ChapterList.Item>) {
        val chapters = items.map { it.chapter }
        screenModelScope.launchNonCancellable {
            val unreadChapterUpdates = chapters
                .asSequence()
                .filter { !it.read }
                .map { ChapterUpdate(id = it.id, read = true) }
                .toList()
            if (unreadChapterUpdates.isNotEmpty()) {
                updateChapter.awaitAll(unreadChapterUpdates)
            }
            deleteChapters(chapters)
            excludeChapter.await(chapters, excluded = true)
        }
        toggleAllSelection(false)
    }

    fun restoreChapters(items: List<ChapterList.Item>) {
        val chapters = items.map { it.chapter }
        screenModelScope.launchNonCancellable {
            excludeChapter.await(chapters, excluded = false)
        }
        toggleAllSelection(false)
    }

    fun toggleShowExcludedChapters() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetShowExcluded(manga, !manga.showExcludedChapters)
        }
    }

    // KMK -->
    fun toggleSkipSubChapterDuplicates() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            val shouldEnableSkipSubChapterDuplicates = !manga.skipSubChapterDuplicates
            val updated = setMangaChapterFlags.awaitSetSkipSubChapterDuplicates(
                manga,
                shouldEnableSkipSubChapterDuplicates,
            )
            if (!updated || !shouldEnableSkipSubChapterDuplicates) return@launchNonCancellable

            val chapters = if (manga.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(
                    mangaId = manga.id,
                    dedupe = false,
                    applyFilter = false,
                )
            } else {
                getMangaAndChapters.awaitChapters(manga.id, applyFilter = false)
            }
            val chapterUpdates = chapters
                .unreadSkippedSubChapterDuplicates()
                .map { chapter -> ChapterUpdate(id = chapter.id, read = true) }
            if (chapterUpdates.isEmpty()) return@launchNonCancellable

            updateChapter.awaitAll(chapterUpdates)
        }
    }

    fun toggleHideNonDownloadedDuplicates() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetHideNonDownloadedDuplicates(
                manga,
                !manga.hideNonDownloadedDuplicates,
            )
        }
    }
    // KMK <--

    fun clearManga(
        deleteDownload: Boolean,
        removeChapters: Boolean,
    ) {
        if (deleteDownload) {
            deleteDownloadedData()
        }
        if (removeChapters) {
            removeChaptersDatabase()
        }
    }

    private fun deleteDownloadedData() {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    if (state.source.id == MERGED_SOURCE_ID) {
                        state.mergedData?.manga
                            ?.forEach { (_, manga) ->
                                val source = state.mergedData.sources.find { it.id != MERGED_SOURCE_ID && manga.source == it.id } ?: return@forEach

                                downloadManager.deleteManga(
                                    manga = manga,
                                    source = source,
                                    removeQueued = true,
                                )
                                if (source.isLocal()) {
                                    // Refresh chapters state for Local source
                                    fetchAllFromSource(
                                        manualFetch = false,
                                        fetchDetails = false,
                                        fetchChapters = true,
                                    )
                                }
                            }
                    } else {
                        downloadManager.deleteManga(
                            manga = state.manga,
                            source = state.source,
                            removeQueued = true,
                        )
                        if (state.source.isLocal()) {
                            // Refresh chapters state for Local source
                            fetchAllFromSource(
                                manualFetch = false,
                                fetchDetails = false,
                                fetchChapters = true,
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun removeChaptersDatabase() {
        screenModelScope.launchNonCancellable {
            try {
                successState?.chapters?.map { it.id }
                    ?.let { deleteChaptersFromDb.await(it) }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }
    // KMK <--

    private fun downloadNewChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            val manga = successState?.manga ?: return@launchNonCancellable
            val chaptersToDownload = filterChaptersForDownload.await(manga, chapters)

            if (chaptersToDownload.isNotEmpty() /* SY --> */ && !manga.isEhBasedManga() /* SY <-- */) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
            // KMK -->
            if (sort == Manga.CHAPTER_SORTING_CUSTOM) {
                initializeCustomSortOrder()
                enterReorderMode()
            }
            // KMK <--
        }
    }

    // KMK -->
    fun reorderChapter(chapterItem: ChapterList.Item, newIndex: Int) {
        val mangaId = successState?.manga?.id ?: return
        screenModelScope.launchNonCancellable {
            reorderChapter.await(mangaId, chapterItem.chapter, newIndex)
        }
    }

    private suspend fun initializeCustomSortOrder() {
        val chapters = successState?.processedChapters ?: return
        // Only initialize if all customSortOrder are null (first time switching to custom)
        if (chapters.any { it.chapter.customSortOrder != null }) return
        val updates = chapters.mapIndexed { index, item ->
            ChapterUpdate(id = item.chapter.id, customSortOrder = index.toLong())
        }
        updateChapter.awaitAll(updates)
    }

    fun enterReorderMode() {
        val chapters = successState?.processedChapters ?: return
        savedChapterOrder = chapters.map { it.chapter.id to it.chapter.customSortOrder }
        updateSuccessState { it.copy(isReorderModeActive = true) }
    }

    fun exitReorderMode(save: Boolean) {
        if (!save) {
            val cached = savedChapterOrder
            if (cached != null) {
                screenModelScope.launchNonCancellable {
                    val updates = cached.map { (id, order) ->
                        ChapterUpdate(id = id, customSortOrder = order)
                    }
                    updateChapter.awaitAll(updates)
                }
            }
        }
        savedChapterOrder = null
        updateSuccessState { it.copy(isReorderModeActive = false) }
    }

    fun moveChapterToTop(chapterItem: ChapterList.Item) {
        val mangaId = successState?.manga?.id ?: return
        screenModelScope.launchNonCancellable {
            reorderChapter.await(mangaId, chapterItem.chapter, 0)
        }
    }

    fun moveChapterToBottom(chapterItem: ChapterList.Item) {
        val mangaId = successState?.manga?.id ?: return
        val size = successState?.processedChapters?.size ?: return
        if (size <= 1) return
        screenModelScope.launchNonCancellable {
            reorderChapter.await(mangaId, chapterItem.chapter, size - 1)
        }
    }

    fun moveChaptersToTop(items: List<ChapterList.Item>) {
        if (items.isEmpty()) return
        val allChapters = successState?.processedChapters ?: return
        val selectedIds = items.mapTo(HashSet()) { it.chapter.id }
        toggleAllSelection(false)
        screenModelScope.launchNonCancellable {
            val selected = allChapters.filter { it.chapter.id in selectedIds }
            val rest = allChapters.filter { it.chapter.id !in selectedIds }
            val updates = (selected + rest).mapIndexed { index, item ->
                ChapterUpdate(id = item.chapter.id, customSortOrder = index.toLong())
            }
            updateChapter.awaitAll(updates)
        }
    }

    fun moveChaptersToBottom(items: List<ChapterList.Item>) {
        if (items.isEmpty()) return
        val allChapters = successState?.processedChapters ?: return
        val selectedIds = items.mapTo(HashSet()) { it.chapter.id }
        toggleAllSelection(false)
        screenModelScope.launchNonCancellable {
            val selected = allChapters.filter { it.chapter.id in selectedIds }
            val rest = allChapters.filter { it.chapter.id !in selectedIds }
            val updates = (rest + selected).mapIndexed { index, item ->
                ChapterUpdate(id = item.chapter.id, customSortOrder = index.toLong())
            }
            updateChapter.awaitAll(updates)
        }
    }

    fun resetCustomSortOrder() {
        val chapters = successState?.processedChapters ?: return
        savedChapterOrder = null
        updateSuccessState { it.copy(isReorderModeActive = false) }
        screenModelScope.launchNonCancellable {
            val updates = chapters.map { ChapterUpdate(id = it.chapter.id, customSortOrder = null) }
            updateChapter.awaitAll(updates)
        }
    }
    // KMK <--

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val selectedIndex = successState.processedChapters.indexOfFirst { it.id == item.chapter.id }
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
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val state = successState
        val manga = state?.manga ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(manga.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { mangaTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                // KMK -->
                val supportedTrackers = source?.let { source ->
                    val sources = if (source is MergedSource) {
                        state.mergedData?.sources ?: emptyList()
                    } else {
                        listOf(source)
                    }
                    loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(sources) ?: true }
                } ?: loggedInTrackers.filterNot { it is EnhancedTracker }
                // KMK <--
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = mangaTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks to supportedTrackers
            }
                // SY -->
                .map { (tracks, supportedTrackers) ->
                    val supportedTrackerTracks = if (manga.source in mangaDexSourceIds ||
                        state.mergedData?.manga?.values.orEmpty().any {
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
        val mdManga = state.manga.takeIf { it.source in mangaDexSourceIds }
            ?: state.mergedData?.manga?.values?.find { it.source in mangaDexSourceIds }
            ?: throw IllegalArgumentException("Could not create initial track")
        val track = trackerManager.mdList.createInitialTracker(state.manga, mdManga)
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
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        data class SetFetchInterval(val manga: Manga) : Dialog

        // SY -->
        data class EditMangaInfo(val manga: Manga) : Dialog
        data class EditMergedSettings(val mergedData: MergedMangaData) : Dialog
        // SY <--

        // KMK -->
        data object ClearManga : Dialog
        data object CommentsSheet : Dialog
        // KMK <--

        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    // KMK -->
    /** In-sheet comments load state. Fetch starts only when [Dialog.CommentsSheet] opens. */
    sealed interface CommentsState {
        data object Idle : CommentsState
        data object Loading : CommentsState
        data class Empty(
            val subtitle: String? = null,
            val totalCount: Int? = null,
        ) : CommentsState
        data class Loaded(
            val comments: List<SourceComment>,
            val page: Int,
            val hasNextPage: Boolean,
            val totalCount: Int? = null,
            val subtitle: String? = null,
            val isLoadingMore: Boolean = false,
        ) : CommentsState
        data class Error(val message: String) : CommentsState
    }
    // KMK <--

    fun dismissDialog() {
        val closingComments = successState?.dialog is Dialog.CommentsSheet
        updateSuccessState { it.copy(dialog = null) }
        if (closingComments) {
            cancelCommentsFetch()
        }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
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

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(target = manga, current = duplicate)) }
    }

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
                    state.copy(dialog = Dialog.EditMangaInfo(state.manga))
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

    // KMK -->
    fun showClearMangaDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ClearManga) }
    }
    // KMK <--

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: ImmutableSet<String>,
            val excludedScanlators: ImmutableSet<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val hideMissingChapters: Boolean = false,

            // SY -->
            val meta: RaisedSearchMetadata?,
            val mergedData: MergedMangaData?,
            val showRecommendationsInOverflow: Boolean,
            val showMergeInOverflow: Boolean,
            val showMergeWithAnother: Boolean,
            val pagePreviewsState: PagePreviewState,
            val alwaysShowReadingProgress: Boolean,
            val previewsRowCount: Int,
            // SY <--
            // KMK -->
            /** App-side MangaFire rating/follows overlay; null while loading or unsupported. */
            val mangaFireOverlay: MangaFireOverlayMetadata? = null,
            /** Comments sheet payload; Idle until sheet opens (1.4a+). */
            val commentsState: CommentsState = CommentsState.Idle,
            /** Last total reported by the provider; feeds the CTA badge after the first load. */
            val commentsTotalCount: Int? = null,
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
            val relatedMangaCollection: List<RelatedManga>? = null,
            val seedColor: Color? = manga.asMangaCover().vibrantCoverColor?.let { Color(it) },
            val isReorderModeActive: Boolean = false,
            val skipDupeEnabled: Boolean = false,
            // KMK <--
        ) : State {
            // KMK -->
            /**
             * a value of null will be treated as still loading, so if all searching were failed and won't update
             * 'relatedMangaCollection` then we should return empty list
             */
            val relatedMangasSorted = relatedMangaCollection
                ?.sorted(manga)
                ?.removeDuplicates(manga)
                ?.filter { it.isVisible() }
                ?.isLoading(isRelatedMangasFetched)
                ?: if (isRelatedMangasFetched == true) emptyList() else null
            // KMK <--

            val processedChapters by lazy {
                chapters.applyFilters(manga).toList()
                    // KMK -->
                    // safe-guard some edge-cases where chapters are duplicated some how on a merged entry
                    .distinctBy { it.id }
                // KMK <--
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            val chapterListItems by lazy {
                // KMK -->
                if (hideMissingChapters || manga.sorting == Manga.CHAPTER_SORTING_CUSTOM) {
                // KMK <--
                    return@lazy processedChapters
                }

                processedChapters.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (manga.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.chapter.chapterNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherChapter.chapter, lowerChapter.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered()

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                // KMK -->
                val dedupedByDownload = if (manga.hideNonDownloadedDuplicates && !isLocalManga) {
                    hideNonDownloadedDuplicates(
                        chapterNumber = { it.chapter.chapterNumber },
                        isDownloaded = { it.isDownloaded },
                    )
                } else {
                    this
                }
                // KMK <--
                return dedupedByDownload.asSequence()
                    // KMK -->
                    .filterNot { (chapter) -> !manga.showExcludedChapters && chapter.excluded }
                    // KMK <--
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
            }
        }
    }
}

// SY -->
data class MergedMangaData(
    val references: List<MergedMangaReference>,
    val manga: Map<Long, Manga>,
    val sources: List<Source>,
)
// SY <--

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
        // SY -->
        val sourceName: String?,
        val showScanlator: Boolean,
        // SY <--
    ) : ChapterList() {
        val id = chapter.id
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
sealed interface RelatedManga {
    data object Loading : RelatedManga

    data class Success(
        val keyword: String,
        val mangaList: List<Manga>,
    ) : RelatedManga {
        val isEmpty: Boolean
            get() = mangaList.isEmpty()

        companion object {
            suspend fun fromPair(
                pair: Pair<String, List<SManga>>,
                toManga: suspend (mangaList: List<SManga>) -> List<Manga>,
            ) = Success(pair.first, toManga(pair.second))
        }
    }

    fun isVisible(): Boolean {
        return this is Loading || (this is Success && !this.isEmpty)
    }

    companion object {
        internal fun List<RelatedManga>.sorted(manga: Manga): List<RelatedManga> {
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

        internal fun List<RelatedManga>.removeDuplicates(manga: Manga): List<RelatedManga> {
            val mangaIds = HashSet<Long>().apply { add(manga.id) }

            return map { relatedManga ->
                if (relatedManga is Success) {
                    Success(
                        relatedManga.keyword,
                        relatedManga.mangaList
                            .filter { mangaIds.add(it.id) },
                    )
                } else {
                    relatedManga
                }
            }
        }

        internal fun List<RelatedManga>.isLoading(isRelatedMangaFetched: Boolean?): List<RelatedManga> {
            return if (isRelatedMangaFetched == false) this + listOf(Loading) else this
        }
    }
}
// KMK <--
