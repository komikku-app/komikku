package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.DeleteByMergeId
import eu.kanade.domain.manga.interactor.DeleteMangaById
import eu.kanade.domain.manga.interactor.DeleteMergeById
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.InsertMergedReference
import eu.kanade.domain.manga.interactor.SetMangaFilteredScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.interactor.UpdateMergedSettings
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.log.xLogD
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.preference.CheckboxState
import tachiyomi.core.preference.mapAsCheckboxState
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetMergedChapterByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.TriStateFilter
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class MangaInfoScreenModel(
    val context: Context,
    val mangaId: Long,
    private val isFromSource: Boolean,
    val smartSearched: Boolean,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    readerPreferences: ReaderPreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
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
    private val deleteMergeById: DeleteMergeById = Injekt.get(),
    private val getFlatMetadata: GetFlatMetadataById = Injekt.get(),
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    // SY <--
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenState>(MangaScreenState.Loading) {

    private val successState: MangaScreenState.Success?
        get() = state.value as? MangaScreenState.Success

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterItem>?
        get() = successState?.chapters

    private val filteredChapters: Sequence<ChapterItem>?
        get() = successState?.processedChapters

    val relativeTime by uiPreferences.relativeTime().asState(coroutineScope)
    val dateFormat by mutableStateOf(UiPreferences.dateFormat(uiPreferences.dateFormat().get()))
    private val skipFiltered by readerPreferences.skipFiltered().asState(coroutineScope)

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    // EXH -->
    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    val redirectFlow: MutableSharedFlow<EXHRedirect> = MutableSharedFlow()

    data class EXHRedirect(val mangaId: Long)

    var dedupe: Boolean = true
    // EXH <--

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

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private fun updateSuccessState(func: (MangaScreenState.Success) -> MangaScreenState.Success) {
        mutableState.update { if (it is MangaScreenState.Success) func(it) else it }
    }

    init {
        coroutineScope.launchIO {
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
                // SY <--
                .collectLatest { (manga, chapters /* SY --> */, flatMetadata, mergedData /* SY <-- */) ->
                    val chapterItems = chapters.toChapterItems(manga /* SY --> */, mergedData /* SY <-- */)
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapterItems,
                            // SY -->
                            meta = raiseMetadata(flatMetadata, it.source),
                            mergedData = mergedData,
                            scanlators = getChapterScanlators(manga, chapters),
                            // SY <--
                        )
                    }
                }
        }

        observeDownloads()

        coroutineScope.launchIO {
            val manga = getMangaAndChapters.awaitManga(mangaId)
            // SY -->
            val chapters = (if (manga.source == MERGED_SOURCE_ID) getMergedChapterByMangaId.await(mangaId) else getMangaAndChapters.awaitChapters(mangaId))
                .toChapterItems(manga, null)
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
            mutableState.update {
                val source = sourceManager.getOrStub(manga.source)
                MangaScreenState.Success(
                    manga = manga,
                    source = source,
                    isFromSource = isFromSource,
                    chapters = chapters,
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
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
                    scanlators = getChapterScanlators(manga, chapters.map { it.chapter }),
                    alwaysShowReadingProgress = readerPreferences.preserveReadingPosition().get() && manga.isEhBasedManga(),
                    // SY <--
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Fetch info-chapters when needed
            if (coroutineScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        coroutineScope.launch {
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
        val state = successState ?: return
        try {
            withIOContext {
                val networkManga = state.source.getMangaDetails(state.manga.toSManga())
                updateManga.awaitUpdateFromSource(state.manga, networkManga, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = e.snackbarMessage)
            }
        }
    }

    // SY -->
    private fun getChapterScanlators(manga: Manga, chapters: List<Chapter>): List<String> {
        return if (manga.isEhBasedManga()) {
            emptyList()
        } else {
            chapters.flatMap { MdUtil.getScanlators(it.scanlator) }
                .distinct()
        }
    }

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
                lastUpdate = manga.lastUpdate + 1,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateMangaInfo(manga.toSManga())
            coroutineScope.launchNonCancellable {
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
            setCustomMangaInfo.set(
                CustomMangaInfo(
                    state.manga.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
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

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        val originalManga = getManga.await(originalMangaId)
            ?: throw IllegalArgumentException(context.getString(R.string.merge_unknown_entry, originalMangaId))
        if (originalManga.source == MERGED_SOURCE_ID) {
            val children = getMergedReferencesById.await(originalMangaId)
            if (children.any { it.mangaSourceId == manga.source && it.mangaUrl == manga.url }) {
                throw IllegalArgumentException(context.getString(R.string.merged_already))
            }

            val mangaReferences = mutableListOf(
                MergedMangaReference(
                    id = -1,
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
                    id = -1,
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
            var mergedManga = Manga.create()
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
                    withNonCancellableContext {
                        existingManga?.id?.let {
                            deleteByMergeId.await(it)
                            deleteMangaById.await(it)
                        }
                    }
                }
                existingManga = getManga.await(mergedManga.url, mergedManga.source)
            }

            mergedManga = networkToLocalManga.await(mergedManga)

            getCategories.await(originalMangaId)
                .let {
                    setMangaCategories.await(mergedManga.id, it.map { it.id })
                }

            val originalMangaReference = MergedMangaReference(
                id = -1,
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
                id = -1,
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
                id = -1,
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
        coroutineScope.launchNonCancellable {
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
        coroutineScope.launchNonCancellable {
            deleteMergeById.await(reference.id)
        }
    }
    // SY <--

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                coroutineScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.delete_downloads_for_manga),
                        actionLabel = context.getString(R.string.action_delete),
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
        coroutineScope.launchIO {
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
                    val duplicate = getDuplicateLibraryManga.await(manga.title)

                    if (duplicate != null) {
                        mutableState.update { state ->
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
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> promptChangeCategories()
                }

                // Finally match with enhanced tracking when available
                val source = state.source
                state.trackItems
                    .map { it.service }
                    .filterIsInstance<EnhancedTrackService>()
                    .filter { it.accept(source) }
                    .forEach { service ->
                        launchIO {
                            try {
                                service.match(manga)?.let { track ->
                                    (service as TrackService).registerTracking(track, mangaId)
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
        coroutineScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { state ->
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

        coroutineScope.launchIO {
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
        coroutineScope.launchIO {
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
        coroutineScope.launchIO {
            downloadManager.statusFlow()
                .filter { /* SY --> */ if (isMergedSource) it.manga.id in mergedIds else /* SY <-- */ it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        coroutineScope.launchIO {
            downloadManager.progressFlow()
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

    private fun List<Chapter>.toChapterItems(
        manga: Manga,
        mergedData: MergedMangaData?,
    ): List<ChapterItem> {
        val isLocal = manga.isLocal()
        // SY -->
        val isExhManga = manga.isEhBasedManga()
        val enabledLanguages = Injekt.get<SourcePreferences>().enabledLanguages().get()
            .filterNot { it in listOf("all", "other") }
        // SY <--
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            // SY -->
            val manga = mergedData?.manga?.get(chapter.mangaId) ?: manga
            val source = mergedData?.sources?.find { manga.source == it.id }?.takeIf { mergedData.sources.size > 2 }
            // SY <--
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isChapterDownloaded(
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

            ChapterItem(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
                // SY -->
                sourceName = source?.getNameForMangaInfo(null, enabledLanguages = enabledLanguages),
                showScanlator = !isExhManga,
                // SY <--
            )
        }
    }

    // SY -->
    private fun getPagePreviews(manga: Manga, source: Source) {
        coroutineScope.launchIO {
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
        val state = successState ?: return
        try {
            withIOContext {
                if (state.source !is MergedSource) {
                    val chapters = state.source.getChapterList(state.manga.toSManga())

                    val newChapters = syncChaptersWithSource.await(
                        chapters,
                        state.manga,
                        state.source,
                    )

                    if (manualFetch) {
                        downloadNewChapters(newChapters)
                    }
                } else {
                    state.source.fetchChaptersForMergedManga(state.manga, manualFetch, true, dedupe)
                }
            }
        } catch (e: Throwable) {
            val message = if (e is NoChaptersException) {
                context.getString(R.string.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                e.snackbarMessage
            }

            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
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
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty().toList() else allChapters.orEmpty()
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

        if (startNow) {
            val chapterId = chapters.singleOrNull()?.id ?: return
            downloadManager.startDownloadNow(chapterId)
        } else {
            downloadChapters(chapters)
        }

        if (!isFavorited && !successState.hasPromptedToAddBefore) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snack_add_to_library),
                    actionLabel = context.getString(R.string.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
                updateSuccessState { successState ->
                    successState.copy(hasPromptedToAddBefore = true)
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterItem>,
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
                val chapterId = items.singleOrNull()?.chapter?.id ?: return
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
        val successState = successState ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }.toList()
        val prevChapters = if (successState.manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        coroutineScope.launchIO {
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
    private fun downloadChapters(chapters: List<Chapter>) {
        val state = successState ?: return
        if (state.source is MergedSource) {
            chapters.groupBy { it.mangaId }.forEach { map ->
                val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                downloadManager.downloadChapters(manga, map.value)
            }
        } else { /* SY <-- */
            val manga = state.manga
            downloadManager.downloadChapters(manga, chapters)
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        coroutineScope.launchIO {
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
        coroutineScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters,
                        state.manga,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        coroutineScope.launchNonCancellable {
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
    fun setUnreadFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    // SY -->
    fun setScanlatorFilter(filteredScanlators: List<String>) {
        val manga = manga ?: return
        coroutineScope.launchIO {
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

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        coroutineScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.getString(R.string.chapter_settings_updated))
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
                val selectedIndex = successState.processedChapters.indexOfFirst { it.chapter.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.chapter.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.chapter.id)
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
                selectedChapterIds.addOrRemove(it.chapter.id, selected)
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
                selectedChapterIds.addOrRemove(it.chapter.id, !it.selected)
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

        coroutineScope.launchIO {
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
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateSuccessState { it.copy(trackItems = trackItems) }
                }
        }
    }

    // SY -->
    private suspend fun createMdListTrack(): TrackItem {
        val state = successState!!
        val mdManga = state.manga.takeIf { it.source in mangaDexSourceIds }
            ?: state.mergedData?.manga?.values?.find { it.source in mangaDexSourceIds }
            ?: throw IllegalArgumentException("Could not create initial track")
        val track = trackManager.mdList.createInitialTracker(state.manga, mdManga)
            .toDomainTrack(false)!!
        insertTrack.await(track)
        return TrackItem(getTracks.await(mangaId).first { it.syncId == TrackManager.MDLIST }.toDbTrack(), trackManager.mdList)
    }
    // SY <--

    // Track sheet - end

    sealed class Dialog {
        data class ChangeCategory(val manga: Manga, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog()
        data class DuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog()

        // SY -->
        data class EditMangaInfo(val manga: Manga) : Dialog()
        data class EditMergedSettings(val mergedData: MergedMangaData) : Dialog()
        // SY <--

        object SettingsSheet : Dialog()
        object TrackSheet : Dialog()
        object FullCover : Dialog()
    }

    fun dismissDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = null)
            }
        }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.DeleteChapters(chapters))
            }
        }
    }

    fun showSettingsDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.SettingsSheet)
            }
        }
    }

    fun showTrackDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.TrackSheet)
                }
            }
        }
    }

    fun showCoverDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.FullCover)
                }
            }
        }
    }

    // SY -->
    fun showEditMangaInfoDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.EditMangaInfo(state.manga))
                }
            }
        }
    }

    fun showEditMergedSettingsDialog() {
        val mergedData = successState?.mergedData ?: return
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.EditMergedSettings(mergedData))
                }
            }
        }
    }
    // SY <--
}

data class MergedMangaData(
    val references: List<MergedMangaReference>,
    val manga: Map<Long, Manga>,
    val sources: List<Source>,
)

sealed class MangaScreenState {
    @Immutable
    object Loading : MangaScreenState()

    @Immutable
    data class Success(
        val manga: Manga,
        val source: Source,
        val isFromSource: Boolean,
        val chapters: List<ChapterItem>,
        val trackItems: List<TrackItem> = emptyList(),
        val isRefreshingData: Boolean = false,
        val dialog: MangaInfoScreenModel.Dialog? = null,
        val hasPromptedToAddBefore: Boolean = false,
        // SY -->
        val meta: RaisedSearchMetadata?,
        val mergedData: MergedMangaData?,
        val showRecommendationsInOverflow: Boolean,
        val showMergeInOverflow: Boolean,
        val showMergeWithAnother: Boolean,
        val pagePreviewsState: PagePreviewState,
        val scanlators: List<String>,
        val alwaysShowReadingProgress: Boolean,
        // SY <--
    ) : MangaScreenState() {

        val processedChapters: Sequence<ChapterItem>
            get() = chapters.applyFilters(manga)

        val trackingAvailable: Boolean
            get() = trackItems.isNotEmpty()

        val trackingCount: Int
            get() = trackItems.count { it.track != null && ((it.service.id == TrackManager.MDLIST && it.track.status != FollowStatus.UNFOLLOWED.int) || it.service.id != TrackManager.MDLIST) }

        /**
         * Applies the view filters to the list of chapters obtained from the database.
         * @return an observable of the list of chapters filtered and sorted.
         */
        private fun List<ChapterItem>.applyFilters(manga: Manga): Sequence<ChapterItem> {
            val isLocalManga = manga.isLocal()
            val unreadFilter = manga.unreadFilter
            val downloadedFilter = manga.downloadedFilter
            val bookmarkedFilter = manga.bookmarkedFilter
            return asSequence()
                .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                // SY -->
                .filter { chapter ->
                    manga.filteredScanlators.isNullOrEmpty() || MdUtil.getScanlators(chapter.chapter.scanlator).any { group -> manga.filteredScanlators!!.contains(group) }
                }
                // SY <--
                .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
        }
    }
}

@Immutable
data class ChapterItem(
    val chapter: Chapter,
    val downloadState: Download.State,
    val downloadProgress: Int,
    val selected: Boolean = false,

    // SY -->
    val sourceName: String?,
    val showScanlator: Boolean,
    // SY <--
) {
    val isDownloaded = downloadState == Download.State.DOWNLOADED
}

val chapterDecimalFormat = DecimalFormat(
    "#.###",
    DecimalFormatSymbols()
        .apply { decimalSeparator = '.' },
)

private val Throwable.snackbarMessage: String
    get() = when (val className = this::class.simpleName) {
        null -> message ?: ""
        "Exception", "HttpException", "IOException", "SourceNotInstalledException" -> message ?: className
        else -> "$className: $message"
    }

// SY -->
sealed class PagePreviewState {
    object Unused : PagePreviewState()
    object Loading : PagePreviewState()
    data class Success(val pagePreviews: List<PagePreview>) : PagePreviewState()
    data class Error(val error: Throwable) : PagePreviewState()
}
// SY <--
