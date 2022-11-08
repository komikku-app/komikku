package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.mapAsCheckboxState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetMangaDefaultChapterFlags
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.DeleteByMergeId
import eu.kanade.domain.manga.interactor.DeleteMangaById
import eu.kanade.domain.manga.interactor.GetDuplicateLibraryManga
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMangaWithChapters
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.InsertMergedReference
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.interactor.SetMangaChapterFlags
import eu.kanade.domain.manga.interactor.SetMangaFilteredScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.interactor.UpdateMergedSettings
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.MergeMangaSettingsUpdate
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.DeleteTrack
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.toRelativeString
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.asHotFlow
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.log.xLogD
import exh.log.xLogE
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.merged.sql.models.MergedMangaReference
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import eu.kanade.domain.chapter.model.Chapter as DomainChapter
import eu.kanade.domain.manga.model.Manga as DomainManga

class MangaPresenter(
    val mangaId: Long,
    val isFromSource: Boolean,
    val smartSearched: Boolean,
    private val basePreferences: BasePreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    // SY -->
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val setMangaFilteredScanlators: SetMangaFilteredScanlators = Injekt.get(),
    private val getMergedChapterByMangaId: GetMergedChapterByMangaId = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val insertMergedReference: InsertMergedReference = Injekt.get(),
    private val updateMergedSettings: UpdateMergedSettings = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val deleteMangaById: DeleteMangaById = Injekt.get(),
    private val deleteByMergeId: DeleteByMergeId = Injekt.get(),
    private val getFlatMetadata: GetFlatMetadataById = Injekt.get(),
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    // SY <--
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val deleteTrack: DeleteTrack = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val syncChaptersWithTrackServiceTwoWay: SyncChaptersWithTrackServiceTwoWay = Injekt.get(),
) : BasePresenter<MangaController>() {

    private val _state: MutableStateFlow<MangaScreenState> = MutableStateFlow(MangaScreenState.Loading)
    val state = _state.asStateFlow()

    private val successState: MangaScreenState.Success?
        get() = state.value as? MangaScreenState.Success

    private var _trackList: List<TrackItem> = emptyList()
    val trackList get() = _trackList

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    private var searchTrackerJob: Job? = null
    private var refreshTrackersJob: Job? = null

    val manga: DomainManga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    val isFavoritedManga: Boolean
        get() = manga?.favorite ?: false

    private val processedChapters: Sequence<ChapterItem>?
        get() = successState?.processedChapters

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list

    // EXH -->
    private val customMangaManager: CustomMangaManager by injectLazy()

    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    val redirectFlow: MutableSharedFlow<EXHRedirect> = MutableSharedFlow()

    data class EXHRedirect(val mangaId: Long, val update: Boolean)

    var dedupe: Boolean = true

    var allChapterScanlators: List<String> = emptyList()
    // EXH <--

    private data class CombineState(
        val manga: DomainManga,
        val chapters: List<DomainChapter>,
        val flatMetadata: FlatMetadata?,
        val mergedData: MergedMangaData? = null,
        val pagePreviewsState: PagePreviewState = PagePreviewState.Loading,
    ) {
        constructor(pair: Pair<DomainManga, List<DomainChapter>>, flatMetadata: FlatMetadata?) :
            this(pair.first, pair.second, flatMetadata)
    }

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private fun updateSuccessState(func: (MangaScreenState.Success) -> MangaScreenState.Success) {
        _state.update { if (it is MangaScreenState.Success) func(it) else it }
    }

    private var incognitoMode = false
        set(value) {
            updateSuccessState { it.copy(isIncognitoMode = value) }
            field = value
        }
    private var downloadedOnlyMode = false
        set(value) {
            updateSuccessState { it.copy(isDownloadedOnlyMode = value) }
            field = value
        }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        val toChapterItemsParams: List<DomainChapter>.(manga: DomainManga /* SY --> */, mergedData: MergedMangaData? /* SY <-- */) -> List<ChapterItem> = { manga, /* SY --> */mergedData /* SY <-- */ ->
            val uiPreferences = Injekt.get<UiPreferences>()
            toChapterItems(
                context = view?.activity ?: Injekt.get<Application>(),
                manga = manga,
                // SY -->
                dateRelativeTime = if (manga.isEhBasedManga()) 0 else uiPreferences.relativeTime().get(),
                dateFormat = if (manga.isEhBasedManga()) {
                    MetadataUtil.EX_DATE_FORMAT
                } else {
                    UiPreferences.dateFormat(uiPreferences.dateFormat().get())
                },
                mergedData = mergedData,
                alwaysShowReadingProgress = readerPreferences.preserveReadingPosition().get() && manga.isEhBasedManga(),
                // SY <--
            )
        }

        // For UI changes
        presenterScope.launchIO {
            getMangaAndChapters.subscribe(mangaId)
                .distinctUntilChanged()
                // SY -->
                .combine(
                    getMergedChapterByMangaId.subscribe(mangaId, true)
                        .distinctUntilChanged(),
                ) { (manga, chapters), mergedChapters ->
                    if (manga.source == MERGED_SOURCE_ID) {
                        manga to mergedChapters
                    } else {
                        manga to chapters
                    }
                }
                .onEach { (manga, chapters) ->
                    if (chapters.isNotEmpty() && manga.isEhBasedManga() && DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled) {
                        // Check for gallery in library and accept manga with lowest id
                        // Find chapters sharing same root
                        updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)
                            .onEach { (acceptedChain, _) ->
                                // Redirect if we are not the accepted root
                                if (manga.id != acceptedChain.manga.id && acceptedChain.manga.favorite) {
                                    // Update if any of our chapters are not in accepted manga's chapters
                                    xLogD("Found accepted manga %s", manga.url)
                                    val ourChapterUrls = chapters.map { it.url }.toSet()
                                    val acceptedChapterUrls = acceptedChain.chapters.map { it.url }.toSet()
                                    val update = (ourChapterUrls - acceptedChapterUrls).isNotEmpty()
                                    redirectFlow.emit(
                                        EXHRedirect(
                                            acceptedChain.manga.id,
                                            update,
                                        ),
                                    )
                                }
                            }.launchIn(presenterScope)
                    }
                    if (!manga.isEhBasedManga()) {
                        allChapterScanlators = chapters.flatMap { MdUtil.getScanlators(it.scanlator) }
                            .distinct()
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
                // SY <--
                .collectLatest { (manga, chapters /* SY --> */, flatMetadata, mergedData /* SY <-- */) ->
                    val chapterItems = chapters.toChapterItemsParams(manga /* SY --> */, mergedData /* SY <-- */)
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

        observeDownloads()

        presenterScope.launchIO {
            val manga = getMangaAndChapters.awaitManga(mangaId)
            // SY -->
            val chapters = (if (manga.source == MERGED_SOURCE_ID) getMergedChapterByMangaId.await(mangaId, true) else getMangaAndChapters.awaitChapters(mangaId))
                .toChapterItemsParams(manga, null)
            val mergedData = getMergedReferencesById.await(mangaId).takeIf { it.isNotEmpty() }?.let { references ->
                MergedMangaData(
                    references,
                    getMergedMangaById.await(mangaId).associateBy { it.id },
                    references.map { it.mangaSourceId }.distinct()
                        .map { sourceManager.getOrStub(it) },
                )
            }
            val meta = getFlatMetadata.await(mangaId)
            // SY <--

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            _state.update {
                val source = sourceManager.getOrStub(manga.source)
                MangaScreenState.Success(
                    manga = manga,
                    source = source,
                    isFromSource = isFromSource,
                    trackingAvailable = trackManager.hasLoggedServices(),
                    chapters = chapters,
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    isIncognitoMode = incognitoMode,
                    isDownloadedOnlyMode = downloadedOnlyMode,
                    dialog = null,
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
                    // SY <--
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()
            observeTrackingCount()

            // Fetch info-chapters when needed
            if (presenterScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }

        basePreferences.incognitoMode()
            .asHotFlow { incognitoMode = it }
            .launchIn(presenterScope)

        basePreferences.downloadedOnly()
            .asHotFlow { downloadedOnlyMode = it }
            .launchIn(presenterScope)
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        presenterScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
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
        withIOContext {
            try {
                successState?.let {
                    val networkManga = it.source.getMangaDetails(it.manga.toSManga())
                    updateManga.awaitUpdateFromSource(it.manga, networkManga, manualFetch)
                }
            } catch (e: Throwable) {
                this@MangaPresenter.xLogE("Error getting manga details", e)
                withUIContext { view?.onFetchMangaInfoError(e) }
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
            val newDesc = description?.trimOrNull()
            manga = manga.copy(
                ogTitle = newTitle,
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateMangaInfo(manga.toSManga())
            presenterScope.launchNonCancellable {
                updateManga.await(
                    MangaUpdate(
                        manga.id,
                        title = newTitle,
                        author = newAuthor,
                        artist = newArtist,
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
            customMangaManager.saveMangaInfo(
                CustomMangaManager.MangaJson(
                    state.manga.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    status.takeUnless { it == state.manga.ogStatus },
                ),
            )
            manga = manga.copy()
        }

        updateSuccessState { successState ->
            successState.copy(manga = manga)
        }
    }

    suspend fun smartSearchMerge(context: Context, manga: DomainManga, originalMangaId: Long): DomainManga {
        val originalManga = getManga.await(originalMangaId)
            ?: throw IllegalArgumentException(context.getString(R.string.merge_unknown_manga, originalMangaId))
        if (originalManga.source == MERGED_SOURCE_ID) {
            val children = getMergedReferencesById.await(originalMangaId)
            if (children.any { it.mangaSourceId == manga.source && it.mangaUrl == manga.url }) {
                throw IllegalArgumentException(context.getString(R.string.merged_already))
            }

            val mangaReferences = mutableListOf(
                MergedMangaReference(
                    id = null,
                    isInfoManga = false,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 0,
                    downloadChapters = true,
                    mergeId = originalManga.id,
                    mergeUrl = originalManga.url,
                    mangaId = manga.id,
                    mangaUrl = manga.url,
                    mangaSourceId = manga.source,
                ),
            )

            if (children.isEmpty() || children.all { it.mangaSourceId != MERGED_SOURCE_ID }) {
                mangaReferences += MergedMangaReference(
                    id = null,
                    isInfoManga = false,
                    getChapterUpdates = false,
                    chapterSortMode = 0,
                    chapterPriority = -1,
                    downloadChapters = false,
                    mergeId = originalManga.id,
                    mergeUrl = originalManga.url,
                    mangaId = originalManga.id,
                    mangaUrl = originalManga.url,
                    mangaSourceId = MERGED_SOURCE_ID,
                )
            }

            // todo
            insertMergedReference.awaitAll(mangaReferences)

            return originalManga
        } else {
            var mergedManga = DomainManga.create()
                .copy(
                    url = originalManga.url,
                    ogTitle = originalManga.title,
                    source = MERGED_SOURCE_ID,
                )
                .copyFrom(originalManga.toSManga())
                .copy(
                    favorite = true,
                    lastUpdate = originalManga.lastUpdate,
                    viewerFlags = originalManga.viewerFlags,
                    chapterFlags = originalManga.chapterFlags,
                    dateAdded = System.currentTimeMillis(),
                )

            var existingManga = getManga.await(mergedManga.url, mergedManga.source)
            while (existingManga != null) {
                if (existingManga.favorite) {
                    throw IllegalArgumentException(context.getString(R.string.merge_duplicate))
                } else {
                    withContext(NonCancellable) {
                        existingManga?.id?.let {
                            deleteByMergeId.await(it)
                            deleteMangaById.await(it)
                        }
                    }
                }
                existingManga = getManga.await(mergedManga.url, mergedManga.source)
            }

            mergedManga = networkToLocalManga.await(mergedManga, mergedManga.source)

            getCategories.await(originalMangaId)
                .let {
                    setMangaCategories.await(mergedManga.id, it.map { it.id })
                }

            val originalMangaReference = MergedMangaReference(
                id = null,
                isInfoManga = true,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = true,
                mergeId = mergedManga.id,
                mergeUrl = mergedManga.url,
                mangaId = originalManga.id,
                mangaUrl = originalManga.url,
                mangaSourceId = originalManga.source,
            )

            val newMangaReference = MergedMangaReference(
                id = null,
                isInfoManga = false,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = true,
                mergeId = mergedManga.id,
                mergeUrl = mergedManga.url,
                mangaId = manga.id,
                mangaUrl = manga.url,
                mangaSourceId = manga.source,
            )

            val mergedMangaReference = MergedMangaReference(
                id = null,
                isInfoManga = false,
                getChapterUpdates = false,
                chapterSortMode = 0,
                chapterPriority = -1,
                downloadChapters = false,
                mergeId = mergedManga.id,
                mergeUrl = mergedManga.url,
                mangaId = mergedManga.id,
                mangaUrl = mergedManga.url,
                mangaSourceId = MERGED_SOURCE_ID,
            )

            insertMergedReference.awaitAll(listOf(originalMangaReference, newMangaReference, mergedMangaReference))

            return mergedManga
        }

        // Note that if the manga are merged in a different order, this won't trigger, but I don't care lol
    }

    fun updateMergeSettings(mergedMangaReferences: List<MergedMangaReference>) {
        presenterScope.launchNonCancellable {
            if (mergedMangaReferences.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    mergedMangaReferences.map {
                        MergeMangaSettingsUpdate(
                            it.id!!,
                            it.isInfoManga,
                            it.getChapterUpdates,
                            it.chapterPriority,
                            it.downloadChapters,
                        )
                    },
                )
            }
        }
    }

    fun toggleDedupe() {
        // I cant find any way to call the chapter list subscription to get the chapters again
    }
    // SY <--

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        onAdded: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        presenterScope.launchIO {
            val manga = state.manga

            if (isFavoritedManga) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.toDbManga().removeCovers() > 0) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryManga.await(manga.title, manga.source)

                    if (duplicate != null) {
                        _state.update { state ->
                            when (state) {
                                MangaScreenState.Loading -> state
                                is MangaScreenState.Success -> state.copy(dialog = Dialog.DuplicateManga(manga, duplicate))
                            }
                        }
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
                        withUIContext { onAdded() }
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                        withUIContext { onAdded() }
                    }

                    // Choose a category
                    else -> promptChangeCategories()
                }

                // Finally match with enhanced tracking when available
                val source = state.source
                trackList
                    .map { it.service }
                    .filterIsInstance<EnhancedTrackService>()
                    .filter { it.accept(source) }
                    .forEach { service ->
                        launchIO {
                            try {
                                service.match(manga.toDbManga())?.let { track ->
                                    registerTracking(track, service as TrackService)
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "Could not match manga: ${manga.title} with service $service"
                                }
                            }
                        }
                    }
            }
        }
    }

    fun promptChangeCategories() {
        val state = successState ?: return
        val manga = state.manga
        presenterScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            _state.update { state ->
                when (state) {
                    MangaScreenState.Loading -> state
                    is MangaScreenState.Success -> state.copy(
                        dialog = Dialog.ChangeCategory(
                            manga = manga,
                            initialSelection = categories.mapAsCheckboxState { it.id in selection },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    fun deleteDownloads() {
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
    private suspend fun getMangaCategoryIds(manga: DomainManga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: DomainManga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (!manga.favorite) {
            presenterScope.launchIO {
                updateManga.awaitUpdateFavorite(manga.id, true)
            }
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
        presenterScope.launchIO {
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

    private fun observeTrackingCount() {
        val manga = successState?.manga ?: return

        presenterScope.launchIO {
            getTracks.subscribe(manga.id)
                .catch { logcat(LogPriority.ERROR, it) }
                .map { tracks ->
                    val loggedServicesId = loggedServices.map { it.id }
                    tracks
                        .filter { it.syncId in loggedServicesId }
                        // SY -->
                        .filterNot { it.syncId == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int.toLong() }
                        // SY <--
                        .size
                }
                .collectLatest { trackingCount ->
                    updateSuccessState { it.copy(trackingCount = trackingCount) }
                }
        }
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        // SY -->
        val isMergedSource = source is MergedSource
        val mergedIds = if (isMergedSource) successState?.mergedData?.manga?.keys.orEmpty() else emptySet()
        // SY <--
        presenterScope.launchIO {
            downloadManager.queue.statusFlow()
                .filter { /* SY --> */ if (isMergedSource) it.manga.id in mergedIds else /* SY <-- */ it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        presenterScope.launchIO {
            downloadManager.queue.progressFlow()
                .filter { /* SY --> */ if (isMergedSource) it.manga.id in mergedIds else /* SY <-- */ it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.chapter.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun List<DomainChapter>.toChapterItems(
        context: Context,
        manga: DomainManga,
        dateRelativeTime: Int,
        dateFormat: DateFormat,
        mergedData: MergedMangaData?,
        alwaysShowReadingProgress: Boolean,
    ): List<ChapterItem> {
        // SY -->
        val isExhManga = manga.isEhBasedManga()
        // SY <--
        return map { chapter ->
            val activeDownload = downloadManager.queue.find { chapter.id == it.chapter.id }
            val chapter = chapter.let { if (mergedData != null) it.toMergedDownloadedChapter() else it }
            val manga = mergedData?.manga?.get(chapter.mangaId) ?: manga
            val downloaded = downloadManager.isChapterDownloaded(
                // SY -->
                chapter.name,
                chapter.scanlator,
                manga.ogTitle,
                manga.source,
                // SY <--
            )
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }
            ChapterItem(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                chapterTitleString = if (manga.displayMode == DomainManga.CHAPTER_DISPLAY_NUMBER) {
                    context.getString(
                        R.string.display_mode_chapter,
                        chapterDecimalFormat.format(chapter.chapterNumber.toDouble()),
                    )
                } else {
                    chapter.name
                },
                dateUploadString = chapter.dateUpload
                    .takeIf { it > 0 }
                    ?.let {
                        Date(it).toRelativeString(
                            context,
                            dateRelativeTime,
                            dateFormat,
                        )
                    },
                readProgressString = chapter.lastPageRead.takeIf { /* SY --> */(!chapter.read || alwaysShowReadingProgress)/* SY <-- */ && it > 0 }?.let {
                    context.getString(
                        R.string.chapter_progress,
                        it + 1,
                    )
                },
                // SY -->
                showScanlator = !isExhManga,
                // SY <--
            )
        }
    }

    // SY -->
    private fun DomainChapter.toMergedDownloadedChapter() = copy(
        scanlator = scanlator?.substringAfter(": "),
    )

    private fun getPagePreviews(manga: DomainManga, source: Source) {
        presenterScope.launchIO {
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
     * Requests an updated list of chapters from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        withIOContext {
            try {
                successState?.let { successState ->
                    if (successState.source !is MergedSource) {
                        val chapters = successState.source.getChapterList(successState.manga.toSManga())

                        val newChapters = syncChaptersWithSource.await(
                            chapters,
                            successState.manga,
                            successState.source,
                        )

                        if (manualFetch) {
                            downloadNewChapters(newChapters)
                        }
                    } else {
                        successState.source.fetchChaptersForMergedManga(successState.manga, manualFetch, true, dedupe)
                    }
                }
            } catch (e: Throwable) {
                withUIContext { view?.onFetchChaptersError(e) }
            }
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): DomainChapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    fun getUnreadChapters(): List<DomainChapter> {
        return successState?.processedChapters
            ?.filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            ?.map { it.chapter }
            ?.toList()
            ?: emptyList()
    }

    fun getUnreadChaptersSorted(): List<DomainChapter> {
        val manga = successState?.manga ?: return emptyList()
        val chapters = getUnreadChapters().sortedWith(getChapterSort(manga))
            // SY -->
            .let {
                if (manga.isEhBasedManga()) it.reversed() else it
            }
        // SY <--
        return if (manga.sortDescending()) chapters.reversed() else chapters
    }

    fun startDownloadingNow(chapterId: Long) {
        downloadManager.startDownloadNow(chapterId)
    }

    fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.queue.find { chapterId == it.chapter.id } ?: return
        downloadManager.deletePendingDownload(activeDownload)
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: DomainChapter) {
        val successState = successState ?: return
        val chapters = processedChapters.orEmpty().map { it.chapter }.toList()
        val prevChapters = if (successState.manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<DomainChapter>, read: Boolean) {
        presenterScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<DomainChapter>) {
        val state = successState ?: return
        if (state.source is MergedSource) {
            chapters.groupBy { it.mangaId }.forEach { map ->
                val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                downloadManager.downloadChapters(manga, map.value.map { it.toMergedDownloadedChapter().toDbChapter() })
            }
        } else { /* SY <-- */
            val manga = state.manga
            downloadManager.downloadChapters(manga, chapters.map { it.toDbChapter() })
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<DomainChapter>, bookmarked: Boolean) {
        presenterScope.launchIO {
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
    fun deleteChapters(chapters: List<DomainChapter>) {
        presenterScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters.map { it.toDbChapter() },
                        state.manga,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<DomainChapter>) {
        presenterScope.launchNonCancellable {
            val manga = successState?.manga ?: return@launchNonCancellable
            val categories = getCategories.await(manga.id).map { it.id }
            if (chapters.isEmpty() || !manga.shouldDownloadNewChapters(categories, downloadPreferences) || manga.isEhBasedManga()) return@launchNonCancellable
            downloadChapters(chapters)
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: State) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            State.IGNORE -> DomainManga.SHOW_ALL
            State.INCLUDE -> DomainManga.CHAPTER_SHOW_UNREAD
            State.EXCLUDE -> DomainManga.CHAPTER_SHOW_READ
        }
        presenterScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: State) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            State.IGNORE -> DomainManga.SHOW_ALL
            State.INCLUDE -> DomainManga.CHAPTER_SHOW_DOWNLOADED
            State.EXCLUDE -> DomainManga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        presenterScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: State) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            State.IGNORE -> DomainManga.SHOW_ALL
            State.INCLUDE -> DomainManga.CHAPTER_SHOW_BOOKMARKED
            State.EXCLUDE -> DomainManga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        presenterScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    // SY -->
    fun setScanlatorFilter(filteredScanlators: List<String>) {
        val manga = manga ?: return
        presenterScope.launchIO {
            setMangaFilteredScanlators.awaitSetFilteredScanlators(manga, filteredScanlators)
        }
    }
    // SY <--

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        presenterScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        presenterScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun toggleSelection(
        item: ChapterItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val modifiedIndex = successState.processedChapters.indexOfFirst { it == item }
                if (modifiedIndex < 0) return@apply

                val oldItem = get(modifiedIndex)
                if ((oldItem.selected && selected) || (!oldItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                var newItem = removeAt(modifiedIndex)
                add(modifiedIndex, newItem.copy(selected = selected))

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = modifiedIndex
                        selectedPositions[1] = modifiedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (modifiedIndex < selectedPositions[0]) {
                            range = modifiedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = modifiedIndex
                        } else if (modifiedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until modifiedIndex
                            selectedPositions[1] = modifiedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            newItem = removeAt(it)
                            add(it, newItem.copy(selected = true))
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (modifiedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (modifiedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (modifiedIndex < selectedPositions[0]) {
                            selectedPositions[0] = modifiedIndex
                        } else if (modifiedIndex > selectedPositions[1]) {
                            selectedPositions[1] = modifiedIndex
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

        presenterScope.launchIO {
            getTracks.subscribe(manga.id)
                .catch { logcat(LogPriority.ERROR, it) }
                .map { tracks ->
                    val dbTracks = tracks.map { it.toDbTrack() }
                    loggedServices
                        // Map to TrackItem
                        .map { service -> TrackItem(dbTracks.find { it.sync_id.toLong() == service.id }, service) }
                        // Show only if the service supports this manga's source
                        .filter { (it.service as? EnhancedTrackService)?.accept(source!!) ?: true }
                }
                // SY -->
                .map { trackItems ->
                    if (manga.source in mangaDexSourceIds || state.mergedData?.manga?.values.orEmpty().any { it.source in mangaDexSourceIds }) {
                        val mdTrack = trackItems.firstOrNull { it.service.id == TrackManager.MDLIST }
                        when {
                            mdTrack == null -> {
                                trackItems
                            }
                            mdTrack.track == null -> {
                                trackItems - mdTrack + createMdListTrack()
                            }
                            else -> trackItems
                        }
                    } else {
                        trackItems
                    }
                }
                // SY <--
                .collectLatest { trackItems ->
                    _trackList = trackItems
                    withContext(Dispatchers.Main) {
                        view?.onNextTrackers(trackItems)
                    }
                }
        }
    }

    // SY -->
    private suspend fun createMdListTrack(): TrackItem {
        val state = successState!!
        val mdManga = state.manga.takeIf { it.source in mangaDexSourceIds }
            ?: state.mergedData?.manga?.values?.find { it.source in mangaDexSourceIds }
            ?: throw IllegalArgumentException("Could not create initial track")
        val track = trackManager.mdList.createInitialTracker(state.manga.toDbManga(), mdManga.toDbManga())
            .toDomainTrack(false)!!
        insertTrack.await(track)
        return TrackItem(getTracks.await(mangaId).first { it.syncId == TrackManager.MDLIST }.toDbTrack(), trackManager.mdList)
    }
    // SY <--

    fun refreshTrackers() {
        refreshTrackersJob?.cancel()
        refreshTrackersJob = presenterScope.launchNonCancellable {
            supervisorScope {
                try {
                    trackList
                        .map {
                            async {
                                val track = it.track ?: return@async null

                                val updatedTrack = it.service.refresh(track)

                                val domainTrack = updatedTrack.toDomainTrack() ?: return@async null
                                insertTrack.await(domainTrack)

                                (it.service as? EnhancedTrackService)?.let { _ ->
                                    val allChapters = successState?.chapters
                                        ?.map { it.chapter } ?: emptyList()

                                    syncChaptersWithTrackServiceTwoWay
                                        .await(allChapters, domainTrack, it.service)
                                }
                            }
                        }
                        .awaitAll()

                    withUIContext { view?.onTrackingRefreshDone() }
                } catch (e: Throwable) {
                    this@MangaPresenter.xLogD("Error registering tracking", e)
                    withUIContext { view?.onTrackingRefreshError(e) }
                }
            }
        }
    }

    fun trackingSearch(query: String, service: TrackService) {
        searchTrackerJob?.cancel()
        searchTrackerJob = presenterScope.launchIO {
            try {
                val results = service.search(query)
                withUIContext { view?.onTrackingSearchResults(results) }
            } catch (e: Throwable) {
                this@MangaPresenter.xLogD("Error searching tracking", e)
                withUIContext { view?.onTrackingSearchResultsError(e) }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        val successState = successState ?: return
        if (item != null) {
            item.manga_id = successState.manga.id
            presenterScope.launchNonCancellable {
                try {
                    val allChapters = successState.chapters.map { it.chapter }
                    val hasReadChapters = allChapters.any { it.read }
                    service.bind(item, hasReadChapters)

                    item.toDomainTrack(idRequired = false)?.let { track ->
                        insertTrack.await(track)

                        // Update chapter progress if newer chapters marked read locally
                        if (hasReadChapters) {
                            val latestLocalReadChapterNumber = allChapters
                                .sortedBy { it.chapterNumber }
                                .takeWhile { it.read }
                                .lastOrNull()
                                ?.chapterNumber?.toDouble() ?: -1.0

                            if (latestLocalReadChapterNumber > track.lastChapterRead) {
                                val updatedTrack = track.copy(
                                    lastChapterRead = latestLocalReadChapterNumber,
                                )
                                setTrackerLastChapterRead(TrackItem(updatedTrack.toDbTrack(), service), latestLocalReadChapterNumber.toInt())
                            }
                        }

                        if (service is EnhancedTrackService) {
                            syncChaptersWithTrackServiceTwoWay.await(allChapters, track, service)
                        }
                    }
                } catch (e: Throwable) {
                    this@MangaPresenter.xLogD("Error registering tracking", e)
                    withUIContext { view?.applicationContext?.toast(e.message) }
                }
            }
        } else {
            unregisterTracking(service)
        }
    }

    fun unregisterTracking(service: TrackService) {
        val manga = successState?.manga ?: return

        presenterScope.launchNonCancellable {
            deleteTrack.await(manga.id, service.id)
        }
    }

    private fun updateRemote(track: Track, service: TrackService) {
        presenterScope.launchNonCancellable {
            try {
                service.update(track)

                track.toDomainTrack(idRequired = false)?.let {
                    insertTrack.await(it)
                }

                withUIContext { view?.onTrackingRefreshDone() }
            } catch (e: Throwable) {
                this@MangaPresenter.xLogD("Error updating tracking", e)
                withUIContext { view?.onTrackingRefreshError(e) }

                // Restart on error to set old values
                observeTrackers()
            }
        }
    }

    fun setTrackerStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (track.status == item.service.getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track, item.service)
    }

    fun setTrackerScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setTrackerLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        if (track.last_chapter_read == 0F && track.last_chapter_read < chapterNumber && track.status != item.service.getRereadingStatus()) {
            track.status = item.service.getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toFloat()
        if (track.total_chapters != 0 && track.last_chapter_read.toInt() == track.total_chapters) {
            track.status = item.service.getCompletionStatus()
        }
        updateRemote(track, item.service)
    }

    fun setTrackerStartDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.started_reading_date = date
        updateRemote(track, item.service)
    }

    fun setTrackerFinishDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.finished_reading_date = date
        updateRemote(track, item.service)
    }

    // Track sheet - end

    fun getSourceOrStub(manga: DomainManga): Source {
        return sourceManager.getOrStub(manga.source)
    }

    sealed class Dialog {
        data class ChangeCategory(val manga: DomainManga, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteChapters(val chapters: List<DomainChapter>) : Dialog()
        data class DuplicateManga(val manga: DomainManga, val duplicate: DomainManga) : Dialog()
        data class DownloadCustomAmount(val max: Int) : Dialog()
    }

    fun dismissDialog() {
        _state.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = null)
            }
        }
    }

    fun showDownloadCustomDialog() {
        val max = processedChapters?.count() ?: return
        _state.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.DownloadCustomAmount(max))
            }
        }
    }

    fun showDeleteChapterDialog(chapters: List<DomainChapter>) {
        _state.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.DeleteChapters(chapters))
            }
        }
    }
}

data class MergedMangaData(
    val references: List<MergedMangaReference>,
    val manga: Map<Long, DomainManga>,
    val sources: List<Source>,
)

sealed class MangaScreenState {
    @Immutable
    object Loading : MangaScreenState()

    @Immutable
    data class Success(
        val manga: DomainManga,
        val source: Source,
        val isFromSource: Boolean,
        val chapters: List<ChapterItem>,
        val trackingAvailable: Boolean = false,
        val trackingCount: Int = 0,
        val isRefreshingData: Boolean = false,
        val isIncognitoMode: Boolean = false,
        val isDownloadedOnlyMode: Boolean = false,
        val dialog: MangaPresenter.Dialog? = null,
        // SY -->
        val meta: RaisedSearchMetadata?,
        val mergedData: MergedMangaData?,
        val showRecommendationsInOverflow: Boolean,
        val showMergeInOverflow: Boolean,
        val showMergeWithAnother: Boolean,
        val pagePreviewsState: PagePreviewState,
        // SY <--
    ) : MangaScreenState() {

        val processedChapters: Sequence<ChapterItem>
            get() = chapters.applyFilters(manga)
    }
}

@Immutable
data class ChapterItem(
    val chapter: DomainChapter,
    val downloadState: Download.State,
    val downloadProgress: Int,

    val chapterTitleString: String,
    val dateUploadString: String?,
    val readProgressString: String?,

    val selected: Boolean = false,

    // SY -->
    val showScanlator: Boolean = true,
    // SY <--
) {
    val isDownloaded = downloadState == Download.State.DOWNLOADED
}

private val chapterDecimalFormat = DecimalFormat(
    "#.###",
    DecimalFormatSymbols()
        .apply { decimalSeparator = '.' },
)

// SY -->
sealed class PagePreviewState {
    object Unused : PagePreviewState()
    object Loading : PagePreviewState()
    data class Success(val pagePreviews: List<PagePreview>) : PagePreviewState()
    data class Error(val error: Throwable) : PagePreviewState()
}
// SY <--
