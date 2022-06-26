package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetDuplicateLibraryManga
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetMangaWithChapters
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.interactor.SetMangaChapterFlags
import eu.kanade.domain.manga.interactor.SetMangaFilteredScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.TriStateFilter
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.manga.model.toMangaInfo
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.chapter.ChapterSettingsHelper
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
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
import exh.source.isEhBasedSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat
import eu.kanade.domain.chapter.model.Chapter as DomainChapter
import eu.kanade.domain.manga.model.Manga as DomainManga

class MangaPresenter(
    val mangaId: Long,
    val isFromSource: Boolean,
    val smartSearched: Boolean,
    private val preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    // SY <--
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    // SY -->
    private val setMangaFilteredScanlators: SetMangaFilteredScanlators = Injekt.get(),
    private val getMergedChapterByMangaId: GetMergedChapterByMangaId = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val getFlatMetadata: GetFlatMetadataById = Injekt.get(),
    // SY <--
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
) : BasePresenter<MangaController>() {

    private val _state: MutableStateFlow<MangaScreenState> = MutableStateFlow(MangaScreenState.Loading)

    val state = _state.asStateFlow()

    private val successState: MangaScreenState.Success?
        get() = state.value as? MangaScreenState.Success

    /**
     * Subscription to update the manga from the source.
     */
    private var fetchMangaJob: Job? = null

    /**
     * Subscription to retrieve the new list of chapters from the source.
     */
    private var fetchChaptersJob: Job? = null

    /**
     * Subscription to observe download status changes.
     */
    private var observeDownloadsStatusSubscription: Subscription? = null
    private var observeDownloadsPageSubscription: Subscription? = null

    private var _trackList: List<TrackItem> = emptyList()
    val trackList get() = _trackList

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    private var trackSubscription: Subscription? = null
    private var searchTrackerJob: Job? = null
    private var refreshTrackersJob: Job? = null

    val manga: DomainManga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    val isFavoritedManga: Boolean
        get() = manga?.favorite ?: false

    val processedChapters: Sequence<ChapterItem>?
        get() = successState?.processedChapters

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

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // Manga info - start

        presenterScope.launchIO {
            if (!getMangaAndChapters.awaitManga(mangaId).favorite) {
                ChapterSettingsHelper.applySettingDefaults(mangaId, setMangaChapterFlags)
            }

            getMangaAndChapters.subscribe(mangaId)
                // SY -->
                .combine(getMergedChapterByMangaId.subscribe(mangaId)) { (manga, chapters), mergedChapters ->
                    if (manga.source == MERGED_SOURCE_ID) {
                        manga to mergedChapters
                    } else manga to chapters
                }
                .onEach { (manga, chapters) ->
                    if (chapters.isNotEmpty() && manga.isEhBasedManga() && DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled) {
                        // Check for gallery in library and accept manga with lowest id
                        // Find chapters sharing same root
                        updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters.map { it.toDbChapter() })
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
                                            acceptedChain.manga.id!!,
                                            update,
                                        ),
                                    )
                                }
                            }.launchIn(presenterScope)
                    }
                    allChapterScanlators = chapters.flatMap { MdUtil.getScanlators(it.scanlator) }.distinct()
                }
                .combine(getFlatMetadata.subscribe(mangaId)) { pair, flatMetadata ->
                    CombineState(pair, flatMetadata)
                }
                .combine(
                    combine(
                        getMergedMangaById.subscribe(mangaId),
                        getMergedReferencesById.subscribe(mangaId),
                    ) { manga, references ->
                        if (manga.isNotEmpty()) {
                            MergedMangaData(references, manga.associateBy { it.id })
                        } else null
                    },
                ) { state, mergedData ->
                    state.copy(mergedData = mergedData)
                }
                // SY <--
                .collectLatest { (manga, chapters, flatMetadata, mergedData) ->
                    val chapterItems = chapters.toChapterItems(manga, mergedData)
                    _state.update { currentState ->
                        when (currentState) {
                            // Initialize success state
                            MangaScreenState.Loading -> {
                                val source = Injekt.get<SourceManager>().getOrStub(manga.source)
                                MangaScreenState.Success(
                                    manga = manga,
                                    source = source,
                                    dateRelativeTime = if (source.isEhBasedSource()) 0 else preferences.relativeTime().get(),
                                    dateFormat = if (source.isEhBasedSource()) {
                                        MetadataUtil.EX_DATE_FORMAT
                                    } else {
                                        preferences.dateFormat()
                                    },
                                    isFromSource = isFromSource,
                                    trackingAvailable = trackManager.hasLoggedServices(),
                                    chapters = chapterItems,
                                    meta = raiseMetadata(flatMetadata, source),
                                    mergedData = mergedData,
                                    showRecommendationsInOverflow = preferences.recommendsInOverflow().get(),
                                    showMergeWithAnother = smartSearched,
                                ).also {
                                    getTrackingObservable(manga)
                                        .subscribeLatestCache(
                                            { _, count ->
                                                successState?.let {
                                                    _state.value = it.copy(trackingCount = count)
                                                }
                                            },
                                            { _, error -> logcat(LogPriority.ERROR, error) },
                                        )
                                }
                            }

                            // Update state
                            is MangaScreenState.Success -> currentState.copy(
                                manga = manga,
                                chapters = chapterItems,
                                // SY -->
                                meta = raiseMetadata(flatMetadata, currentState.source),
                                mergedData = mergedData,
                                // SY <--
                            )
                        }
                    }

                    fetchTrackers()
                    observeDownloads()

                    if (!manga.initialized) {
                        fetchAllFromSource(manualFetch = false)
                    }
                }
        }

        preferences.incognitoMode()
            .asImmediateFlow { incognito ->
                updateSuccessState { it.copy(isIncognitoMode = incognito) }
            }
            .launchIn(presenterScope)

        preferences.downloadedOnly()
            .asImmediateFlow { downloadedOnly ->
                updateSuccessState { it.copy(isDownloadedOnlyMode = downloadedOnly) }
            }
            .launchIn(presenterScope)
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        fetchMangaFromSource(manualFetch)
        fetchChaptersFromSource(manualFetch)
    }

    // Manga info - start

    private fun getTrackingObservable(manga: DomainManga): Observable<Int> {
        if (!trackManager.hasLoggedServices()) {
            return Observable.just(0)
        }

        return db.getTracks(manga.id).asRxObservable()
            .map { tracks ->
                val loggedServices = trackManager.services.filter { it.isLogged }.map { it.id }
                tracks
                    // SY -->
                    .filterNot { it.sync_id == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int }
                    // SY <--
                    .filter { it.sync_id in loggedServices }
            }
            .map { it.size }
    }

    /**
     * Fetch manga information from source.
     */
    private fun fetchMangaFromSource(manualFetch: Boolean = false) {
        if (fetchMangaJob?.isActive == true) return
        fetchMangaJob = presenterScope.launchIO {
            updateSuccessState { it.copy(isRefreshingInfo = true) }
            try {
                successState?.let {
                    val networkManga = it.source.getMangaDetails(it.manga.toMangaInfo())
                    updateManga.awaitUpdateFromSource(it.manga, networkManga, manualFetch)
                }
            } catch (e: Throwable) {
                this@MangaPresenter.xLogE("Error getting manga details", e)
                withUIContext { view?.onFetchMangaInfoError(e) }
            }
            updateSuccessState { it.copy(isRefreshingInfo = false) }
        }
    }

    // SY -->
    private fun raiseMetadata(flatMetadata: FlatMetadata?, source: Source): RaisedSearchMetadata? {
        return if (flatMetadata != null) {
            val metaClass = source.getMainSource<MetadataSource<*, *>>()?.metaClass
            if (metaClass != null) flatMetadata.raise(metaClass) else null
        } else null
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
            manga = manga.copy(
                ogTitle = if (title.isNullOrBlank()) manga.url else title.trim(),
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateMangaInfo(manga.toSManga())
            // TODO
            db.updateMangaInfo(manga.toDbManga()).executeAsBlocking()
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

    suspend fun smartSearchMerge(context: Context, manga: DomainManga, originalMangaId: Long): Manga {
        val originalManga = db.getManga(originalMangaId).executeAsBlocking()
            ?: throw IllegalArgumentException(context.getString(R.string.merge_unknown_manga, originalMangaId))
        if (originalManga.source == MERGED_SOURCE_ID) {
            val children = db.getMergedMangaReferences(originalMangaId).executeAsBlocking()
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
                    mergeId = originalManga.id!!,
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
                    mergeId = originalManga.id!!,
                    mergeUrl = originalManga.url,
                    mangaId = originalManga.id!!,
                    mangaUrl = originalManga.url,
                    mangaSourceId = MERGED_SOURCE_ID,
                )
            }

            db.insertMergedMangas(mangaReferences).executeAsBlocking()

            return originalManga
        } else {
            val mergedManga = Manga.create(originalManga.url, originalManga.title, MERGED_SOURCE_ID).apply {
                copyFrom(originalManga)
                favorite = true
                last_update = originalManga.last_update
                viewer_flags = originalManga.viewer_flags
                chapter_flags = originalManga.chapter_flags
                sorting = Manga.CHAPTER_SORTING_NUMBER
                date_added = System.currentTimeMillis()
            }
            var existingManga = db.getManga(mergedManga.url, mergedManga.source).executeAsBlocking()
            while (existingManga != null) {
                if (existingManga.favorite) {
                    throw IllegalArgumentException(context.getString(R.string.merge_duplicate))
                } else if (!existingManga.favorite) {
                    withContext(NonCancellable) {
                        db.deleteManga(existingManga!!).executeAsBlocking()
                        db.deleteMangaForMergedManga(existingManga!!.id!!).executeAsBlocking()
                    }
                }
                existingManga = db.getManga(mergedManga.url, mergedManga.source).executeAsBlocking()
            }

            // Reload chapters immediately
            mergedManga.initialized = false

            val newId = db.insertManga(mergedManga).executeAsBlocking().insertedId()
            if (newId != null) mergedManga.id = newId

            db.getCategoriesForManga(originalManga)
                .executeAsBlocking()
                .map { MangaCategory.create(mergedManga, it) }
                .let {
                    db.insertMangasCategories(it).executeAsBlocking()
                }

            val originalMangaReference = MergedMangaReference(
                id = null,
                isInfoManga = true,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = true,
                mergeId = mergedManga.id!!,
                mergeUrl = mergedManga.url,
                mangaId = originalManga.id!!,
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
                mergeId = mergedManga.id!!,
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
                mergeId = mergedManga.id!!,
                mergeUrl = mergedManga.url,
                mangaId = mergedManga.id!!,
                mangaUrl = mergedManga.url,
                mangaSourceId = MERGED_SOURCE_ID,
            )

            db.insertMergedMangas(listOf(originalMangaReference, newMangaReference, mergedMangaReference)).executeAsBlocking()

            return mergedManga
        }

        // Note that if the manga are merged in a different order, this won't trigger, but I don't care lol
    }

    fun updateMergeSettings(mergeReference: MergedMangaReference?, mergedMangaReferences: List<MergedMangaReference>) {
        launchIO {
            mergeReference?.let {
                db.updateMergeMangaSettings(it).executeAsBlocking()
            }
            if (mergedMangaReferences.isNotEmpty()) db.updateMergedMangaSettings(mergedMangaReferences).executeAsBlocking()
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
        onRequireCategory: (manga: DomainManga, availableCats: List<Category>) -> Unit,
        onDuplicateExists: ((DomainManga) -> Unit)?,
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
                    launchUI { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (onDuplicateExists != null) {
                    val duplicate = getDuplicateLibraryManga.await(manga.title, manga.source)
                    if (duplicate != null) {
                        launchUI { onDuplicateExists(duplicate) }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = preferences.defaultCategory()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(manga.toDbManga(), defaultCategory)
                        launchUI { onAdded() }
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0 || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(manga.toDbManga(), null)
                        launchUI { onAdded() }
                    }

                    // Choose a category
                    else -> launchUI { onRequireCategory(manga, categories) }
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

    /**
     * Returns true if the manga has any downloads.
     */
    fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga.toDbManga()) > 0
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
                downloadManager.deleteManga(manga.toDbManga(), source)
            }
        } else /* SY <-- */ downloadManager.deleteManga(state.manga.toDbManga(), state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    fun getMangaCategoryIds(manga: DomainManga): Array<Int> {
        val categories = db.getCategoriesForManga(manga.toDbManga()).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Category>) {
        moveMangaToCategories(manga, categories)
        presenterScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id!!, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param manga the manga to move.
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(manga: Manga, categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { MangaCategory.create(manga, it) }
        db.setMangaCategories(mc, listOf(manga))
    }

    /**
     * Move the given manga to the category.
     *
     * @param manga the manga to move.
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(manga: Manga, category: Category?) {
        moveMangaToCategories(manga, listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        // SY -->
        val isMergedSource = source is MergedSource
        val mergedIds = if (isMergedSource) successState?.mergedData?.manga?.keys.orEmpty() else emptySet()
        // SY <--
        observeDownloadsStatusSubscription?.let { remove(it) }
        observeDownloadsStatusSubscription = downloadManager.queue.getStatusObservable()
            .observeOn(Schedulers.io())
            .onBackpressureBuffer()
            .filter { download -> /* SY --> */ if (isMergedSource) download.manga.id in mergedIds else /* SY <-- */ download.manga.id == successState?.manga?.id }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(
                { _, it -> updateDownloadState(it) },
                { _, error ->
                    logcat(LogPriority.ERROR, error)
                },
            )

        observeDownloadsPageSubscription?.let { remove(it) }
        observeDownloadsPageSubscription = downloadManager.queue.getProgressObservable()
            .observeOn(Schedulers.io())
            .onBackpressureBuffer()
            .filter { download -> /* SY --> */ if (isMergedSource) download.manga.id in mergedIds else /* SY <-- */ download.manga.id == successState?.manga?.id }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(
                { _, download -> updateDownloadState(download) },
                { _, error -> logcat(LogPriority.ERROR, error) },
            )
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

    private fun List<DomainChapter>.toChapterItems(manga: DomainManga, mergedData: MergedMangaData?): List<ChapterItem> {
        return map { chapter ->
            val activeDownload = downloadManager.queue.find { chapter.id == it.chapter.id }
            val downloaded = downloadManager.isChapterDownloaded(
                // SY -->
                chapter.let { if (mergedData != null) it.toMergedDownloadedChapter() else it }
                    .toDbChapter(),
                (mergedData?.manga?.get(chapter.mangaId) ?: manga).toDbManga(),
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
            )
        }
    }

    private fun DomainChapter.toMergedDownloadedChapter() = copy(
        scanlator = scanlator?.substringAfter(": "),
    )

    /**
     * Requests an updated list of chapters from the source.
     */
    private fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        if (fetchChaptersJob?.isActive == true) return
        fetchChaptersJob = presenterScope.launchIO {
            updateSuccessState { it.copy(isRefreshingChapter = true) }
            try {
                successState?.let { successState ->
                    if (successState.source !is MergedSource) {
                        val chapters = successState.source.getChapterList(successState.manga.toMangaInfo())
                            .map { it.toSChapter() }

                        val (newChapters, _) = syncChaptersWithSource.await(
                            chapters,
                            successState.manga,
                            successState.source,
                        )

                        if (manualFetch) {
                            val dbChapters = newChapters.map { it.toDbChapter() }
                            downloadNewChapters(dbChapters)
                        }
                    } else {
                        successState.source.fetchChaptersForMergedManga(successState.manga, manualFetch, true, dedupe)
                        Unit
                    }
                }
            } catch (e: Throwable) {
                withUIContext { view?.onFetchChaptersError(e) }
            }
            updateSuccessState { it.copy(isRefreshingChapter = false) }
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): DomainChapter? {
        val successState = successState ?: return null
        return successState.processedChapters.map { it.chapter }.let { chapters ->
            if (successState.manga.sortDescending()) {
                chapters.findLast { !it.read }
            } else {
                chapters.find { !it.read }
            }
        }
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
        val chapters = successState.chapters.map { it.chapter }
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
            val modified = chapters.filterNot { it.read == read }
            modified
                .map { ChapterUpdate(id = it.id, read = read) }
                .forEach { updateChapter.await(it) }
            if (read && preferences.removeAfterMarkedAsRead()) {
                deleteChapters(modified)
            }
        }
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
                downloadManager.downloadChapters(manga.toDbManga(), map.value.map { it.toMergedDownloadedChapter().toDbChapter() })
            }
        } else { /* SY <-- */
            val manga = state.manga
            downloadManager.downloadChapters(manga.toDbManga(), chapters.map { it.toDbChapter() })
        }
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
                .forEach { updateChapter.await(it) }
        }
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<DomainChapter>) {
        launchIO {
            val chapters2 = chapters.map { it.toDbChapter() }
            try {
                updateSuccessState { successState ->
                    val deletedIds = downloadManager
                        .deleteChapters(chapters2, successState.manga.toDbManga(), successState.source)
                        .map { it.id }
                    val deletedChapters = successState.chapters.filter { deletedIds.contains(it.chapter.id) }
                    if (deletedChapters.isEmpty()) return@updateSuccessState successState

                    // TODO: Don't do this fake status update
                    val newChapters = successState.chapters.toMutableList().apply {
                        deletedChapters.forEach {
                            val index = indexOf(it)
                            val toAdd = removeAt(index)
                                .copy(downloadState = Download.State.NOT_DOWNLOADED, downloadProgress = 0)
                            add(index, toAdd)
                        }
                    }
                    successState.copy(chapters = newChapters)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        val manga = successState?.manga ?: return
        if (chapters.isEmpty() || !manga.shouldDownloadNewChapters(db, preferences) || manga.isEhBasedManga()) return
        downloadChapters(chapters.map { it.toDomainChapter()!! })
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
        presenterScope.launchIO {
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
        presenterScope.launchIO {
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
        presenterScope.launchIO {
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
        presenterScope.launchIO {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return
        presenterScope.launchIO {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun fetchTrackers() {
        val state = successState ?: return
        val manga = successState?.manga ?: return
        trackSubscription?.let { remove(it) }
        trackSubscription = db.getTracks(manga.id)
            .asRxObservable()
            .map { tracks ->
                loggedServices.map { service ->
                    TrackItem(tracks.find { it.sync_id == service.id }, service)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
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
                } else trackItems
            }
            // SY <--
            .doOnNext { _trackList = it }
            .subscribeLatestCache(MangaController::onNextTrackers)
    }

    // SY -->
    private fun createMdListTrack(): TrackItem {
        val state = successState!!
        val mdManga = state.manga.takeIf { it.source in mangaDexSourceIds }
            ?: state.mergedData?.manga?.values?.find { it.source in mangaDexSourceIds }
            ?: throw IllegalArgumentException("Could not create initial track")
        val track = trackManager.mdList.createInitialTracker(state.manga.toDbManga(), mdManga.toDbManga())
        track.id = db.insertTrack(track).executeAsBlocking().insertedId()
        return TrackItem(track, trackManager.mdList)
    }
    // SY <--

    fun refreshTrackers() {
        refreshTrackersJob?.cancel()
        refreshTrackersJob = launchIO {
            supervisorScope {
                try {
                    trackList
                        .filter { it.track != null }
                        .map {
                            async {
                                val track = it.service.refresh(it.track!!)
                                db.insertTrack(track).executeAsBlocking()

                                if (it.service is EnhancedTrackService) {
                                    val allChapters = successState?.chapters
                                        ?.map { it.chapter.toDbChapter() } ?: emptyList()
                                    syncChaptersWithTrackServiceTwoWay(db, allChapters, track, it.service)
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
        searchTrackerJob = launchIO {
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
            launchIO {
                try {
                    val allChapters = successState.chapters
                        .map { it.chapter.toDbChapter() }
                    val hasReadChapters = allChapters.any { it.read }
                    service.bind(item, hasReadChapters)
                    db.insertTrack(item).executeAsBlocking()

                    if (service is EnhancedTrackService) {
                        syncChaptersWithTrackServiceTwoWay(db, allChapters, item, service)
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
        db.deleteTrackForManga(manga.toDbManga(), service).executeAsBlocking()
    }

    private fun updateRemote(track: Track, service: TrackService) {
        launchIO {
            try {
                service.update(track)
                db.insertTrack(track).executeAsBlocking()
                withUIContext { view?.onTrackingRefreshDone() }
            } catch (e: Throwable) {
                this@MangaPresenter.xLogD("Error updating tracking", e)
                withUIContext { view?.onTrackingRefreshError(e) }

                // Restart on error to set old values
                fetchTrackers()
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
}

data class MergedMangaData(val references: List<MergedMangaReference>, val manga: Map<Long, DomainManga>)

sealed class MangaScreenState {
    @Immutable
    object Loading : MangaScreenState()

    @Immutable
    data class Success(
        val manga: DomainManga,
        val source: Source,
        val dateRelativeTime: Int,
        val dateFormat: DateFormat,
        val isFromSource: Boolean,
        val chapters: List<ChapterItem>,
        val trackingAvailable: Boolean = false,
        val trackingCount: Int = 0,
        val isRefreshingInfo: Boolean = false,
        val isRefreshingChapter: Boolean = false,
        val isIncognitoMode: Boolean = false,
        val isDownloadedOnlyMode: Boolean = false,
        // SY -->
        val meta: RaisedSearchMetadata?,
        val mergedData: MergedMangaData?,
        val showRecommendationsInOverflow: Boolean,
        val showMergeWithAnother: Boolean,
        // SY <--
    ) : MangaScreenState() {

        val processedChapters: Sequence<ChapterItem>
            get() = chapters.applyFilters(manga)

        /**
         * Applies the view filters to the list of chapters obtained from the database.
         * @return an observable of the list of chapters filtered and sorted.
         */
        private fun List<ChapterItem>.applyFilters(manga: DomainManga): Sequence<ChapterItem> {
            val isLocalManga = manga.isLocal()
            val unreadFilter = manga.unreadFilter
            val downloadedFilter = manga.downloadedFilter
            val bookmarkedFilter = manga.bookmarkedFilter
            val filteredScanlators = manga.filteredScanlators.orEmpty()
            return asSequence()
                .filter { (chapter) ->
                    when (unreadFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> !chapter.read
                        TriStateFilter.ENABLED_NOT -> chapter.read
                    }
                }
                .filter { (chapter) ->
                    when (bookmarkedFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> chapter.bookmark
                        TriStateFilter.ENABLED_NOT -> !chapter.bookmark
                    }
                }
                .filter {
                    when (downloadedFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> it.isDownloaded || isLocalManga
                        TriStateFilter.ENABLED_NOT -> !it.isDownloaded && !isLocalManga
                    }
                }
                .filter { (chapter) ->
                    filteredScanlators.isEmpty() || MdUtil.getScanlators(chapter.scanlator).any { group -> filteredScanlators.contains(group) }
                }
                .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
        }
    }
}

@Immutable
data class ChapterItem(
    val chapter: DomainChapter,
    val downloadState: Download.State,
    val downloadProgress: Int,
) {
    val isDownloaded = downloadState == Download.State.DOWNLOADED
}
