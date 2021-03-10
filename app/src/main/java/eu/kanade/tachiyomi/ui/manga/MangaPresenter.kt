package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.chapter.ChapterSettingsHelper
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.updateCoverLastModified
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.log.xLogD
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.md.utils.scanlatorList
import exh.merged.sql.models.MergedMangaReference
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadataAsync
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedSource
import exh.source.mangaDexSourceIds
import exh.util.shouldDeleteChapters
import exh.util.trimOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import rx.Observable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Date

class MangaPresenter(
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get()
) : BasePresenter<MangaController>() {

    /**
     * Subscription to update the manga from the source.
     */
    private var fetchMangaJob: Job? = null

    /**
     * List of chapters of the manga. It's always unfiltered and unsorted.
     */
    var chapters: List<ChapterItem> = emptyList()
        private set

    /**
     * Subject of list of chapters to allow updating the view without going to DB.
     */
    private val chaptersRelay: PublishRelay<List<ChapterItem>> by lazy {
        PublishRelay.create<List<ChapterItem>>()
    }

    /**
     * Whether the chapter list has been requested to the source.
     */
    var hasRequested = false
        private set

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

    // EXH -->
    private val customMangaManager: CustomMangaManager by injectLazy()

    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    val redirectFlow: MutableSharedFlow<EXHRedirect> = MutableSharedFlow()

    data class EXHRedirect(val manga: Manga, val update: Boolean)

    var meta: RaisedSearchMetadata? = null

    private var mergedManga = emptyList<Manga>()

    var dedupe: Boolean = true

    var allChapterScanlators: Set<String> = emptySet()
    // EXH <--

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // SY -->
        if (source is MergedSource) {
            launchIO { mergedManga = db.getMergedMangas(manga.id!!).executeAsBlocking() }
        }
        // SY <--

        if (!manga.favorite) {
            ChapterSettingsHelper.applySettingDefaults(manga)
        }

        // Manga info - start

        getMangaObservable()
            .observeOn(AndroidSchedulers.mainThread())
            // SY -->
            .flatMap { manga ->
                if (manga.initialized && source.getMainSource() is MetadataSource<*, *>) {
                    getMangaMetaSingle().map {
                        manga to it
                    }.toObservable()
                } else {
                    Observable.just(manga to null)
                }
            }
            .subscribeLatestCache({ view, (manga, flatMetadata) ->
                flatMetadata?.let { metadata ->
                    view.onNextMetaInfo(metadata)
                    meta?.let {
                        it.filteredScanlators?.let {
                            if (chapters.isNotEmpty()) chaptersRelay.call(chapters)
                        }
                    }
                }
                // SY <--
                view.onNextMangaInfo(manga, source)
            })

        getTrackingObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(MangaController::onTrackingCount) { _, error -> Timber.e(error) }

        // Prepare the relay.
        chaptersRelay.flatMap { applyChapterFilters(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(MangaController::onNextChapters) { _, error -> Timber.e(error) }

        // Manga info - end

        // Chapters list - start

        // Add the subscription that retrieves the chapters from the database, keeps subscribed to
        // changes, and sends the list of chapters to the relay.
        add(
            (/* SY --> */if (source is MergedSource) source.getChaptersObservable(manga, true, dedupe) else /* SY <-- */ db.getChapters(manga).asRxObservable())
                .map { chapters ->
                    // Convert every chapter to a model.
                    chapters.map { it.toModel() }
                }
                .doOnNext { chapters ->
                    // Find downloaded chapters
                    setDownloadedChapters(chapters)

                    allChapterScanlators = chapters.flatMap { it.chapter.scanlatorList() }.toSet()

                    // Store the last emission
                    this.chapters = chapters

                    // Listen for download status changes
                    observeDownloads()

                    // SY -->
                    if (chapters.isNotEmpty() && (source.isEhBasedSource()) && DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled) {
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
                                    redirectFlow.tryEmit(
                                        EXHRedirect(
                                            acceptedChain.manga,
                                            update
                                        )
                                    )
                                }
                            }.launchIn(presenterScope)
                    }
                    // SY <--
                }
                .subscribe { chaptersRelay.call(it) }
        )

        // Chapters list - end

        fetchTrackers()
    }

    // Manga info - start

    private fun getMangaObservable(): Observable<Manga> {
        return db.getManga(manga.url, manga.source).asRxObservable()
    }

    private fun getTrackingObservable(): Observable<Int> {
        if (!trackManager.hasLoggedServices()) {
            return Observable.just(0)
        }

        return db.getTracks(manga).asRxObservable()
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

    // SY -->
    private fun getMangaMetaSingle(): Single<FlatMetadata?> {
        val mangaId = manga.id
        return if (mangaId != null) {
            db.getFlatMetadataForManga(mangaId).asRxSingle()
        } else Single.just(null)
    }
    // SY <--

    /**
     * Fetch manga information from source.
     */
    fun fetchMangaFromSource(manualFetch: Boolean = false) {
        if (fetchMangaJob?.isActive == true) return
        fetchMangaJob = presenterScope.launchIO {
            try {
                val networkManga = source.getMangaDetails(manga.toMangaInfo())
                val sManga = networkManga.toSManga()
                manga.prepUpdateCover(coverCache, sManga, manualFetch)
                manga.copyFrom(sManga)
                manga.initialized = true
                db.insertManga(manga).executeAsBlocking()

                withUIContext { view?.onFetchMangaInfoDone() }
            } catch (e: Throwable) {
                withUIContext { view?.onFetchMangaInfoError(e) }
            }
        }
    }

    // SY -->
    fun updateMangaInfo(
        context: Context,
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        tags: List<String>?,
        status: Int?,
        uri: Uri?,
        resetCover: Boolean = false
    ) {
        if (manga.source == LocalSource.ID) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString = tags?.joinToString()
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            manga.status = status ?: 0
            (sourceManager.get(LocalSource.ID) as LocalSource).updateMangaInfo(manga)
            db.updateMangaInfo(manga).executeAsBlocking()
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags.joinToString() != manga.originalGenre) {
                tags
            } else {
                null
            }
            val manga = CustomMangaManager.MangaJson(
                manga.id!!,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                description?.trimOrNull(),
                genre,
                status.takeUnless { it == manga.originalStatus }
            )
            customMangaManager.saveMangaInfo(manga)
        }

        if (uri != null) {
            editCoverWithStream(context, uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            manga.updateCoverLastModified(db)
        }

        if (uri == null && resetCover) {
            Observable.just(manga)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(
                    { view, _ ->
                        view.setRefreshing()
                    }
                )
            fetchMangaFromSource(manualFetch = true)
        } else {
            Observable.just(manga)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(
                    { view, _ ->
                        view.onNextMangaInfo(manga, source)
                    }
                )
        }
    }

    fun editCoverWithStream(context: Context, uri: Uri): Boolean {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return false
        if (manga.source == LocalSource.ID) {
            val cover = LocalSource.updateCover(context, manga, inputStream)
            if (manga.thumbnail_url.isNullOrBlank() && cover != null) {
                manga.thumbnail_url = cover.absolutePath
                db.updateMangaThumbnail(manga).executeAsBlocking()
            }
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            manga.updateCoverLastModified(db)
            return true
        }
        return false
    }

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        val originalManga = db.getManga(originalMangaId).executeAsBlocking() ?: throw IllegalArgumentException("Unknown manga ID: $originalMangaId")
        if (originalManga.source == MERGED_SOURCE_ID) {
            val children = db.getMergedMangaReferences(originalMangaId).executeAsBlocking()
            if (children.any { it.mangaSourceId == manga.source && it.mangaUrl == manga.url }) {
                throw IllegalArgumentException("This manga is already merged with the current manga!")
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
                    mangaId = manga.id!!,
                    mangaUrl = manga.url,
                    mangaSourceId = manga.source
                )
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
                    mangaSourceId = MERGED_SOURCE_ID
                )
            }

            db.insertMergedMangas(mangaReferences).executeAsBlocking()

            return originalManga
        } else {
            val mergedManga = Manga.create(originalManga.url, originalManga.title, MERGED_SOURCE_ID).apply {
                copyFrom(originalManga)
                favorite = true
                last_update = originalManga.last_update
                viewer = originalManga.viewer
                chapter_flags = originalManga.chapter_flags
                sorting = Manga.SORTING_NUMBER
                date_added = System.currentTimeMillis()
            }
            var existingManga = db.getManga(mergedManga.url, mergedManga.source).executeAsBlocking()
            while (existingManga != null) {
                if (existingManga.favorite) {
                    throw IllegalArgumentException("This merged manga is a duplicate!")
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
                mangaSourceId = originalManga.source
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
                mangaId = manga.id!!,
                mangaUrl = manga.url,
                mangaSourceId = manga.source
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
                mangaSourceId = MERGED_SOURCE_ID
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

    fun shareCover(context: Context): File {
        val destDir = File(context.cacheDir, "shared_image")
        return saveCover(destDir)
    }

    fun saveCover(context: Context) {
        val directory = File(
            Environment.getExternalStorageDirectory().absolutePath +
                File.separator + Environment.DIRECTORY_PICTURES +
                File.separator + context.getString(R.string.app_name)
        )
        saveCover(directory)
    }

    private fun saveCover(directory: File): File {
        val cover = coverCache.getCoverFile(manga) ?: throw Exception("Cover url was null")
        if (!cover.exists()) throw Exception("Cover not in cache")
        val type = ImageUtil.findImageType(cover.inputStream())
            ?: throw Exception("Not an image")

        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title}.${type.extension}")

        val destFile = File(directory, filename)
        cover.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    // SY <--

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     *
     * @return the new status of the manga.
     */
    fun toggleFavorite(): Boolean {
        manga.favorite = !manga.favorite
        manga.date_added = when (manga.favorite) {
            true -> Date().time
            false -> 0
        }
        if (!manga.favorite) {
            manga.removeCovers(coverCache)
        }
        db.insertManga(manga).executeAsBlocking()
        return manga.favorite
    }

    /**
     * Returns true if the manga has any downloads.
     */
    fun hasDownloads(): Boolean {
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    fun deleteDownloads() {
        // SY -->
        if (source is MergedSource) {
            val mergedManga = mergedManga.map { it to sourceManager.getOrStub(it.source) }
            mergedManga.forEach { (manga, source) ->
                downloadManager.deleteManga(manga, source)
            }
        } else /* SY <-- */ downloadManager.deleteManga(manga, source)
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
    fun getMangaCategoryIds(manga: Manga): Array<Int> {
        val categories = db.getCategoriesForManga(manga).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    /**
     * Move the given manga to categories.
     *
     * @param manga the manga to move.
     * @param categories the selected categories.
     */
    fun moveMangaToCategories(manga: Manga, categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { MangaCategory.create(manga, it) }
        db.setMangaCategories(mc, listOf(manga))
    }

    /**
     * Move the given manga to the category.
     *
     * @param manga the manga to move.
     * @param category the selected category, or null for default category.
     */
    fun moveMangaToCategory(manga: Manga, category: Category?) {
        moveMangaToCategories(manga, listOfNotNull(category))
    }

    /**
     * Update cover with local file.
     *
     * @param manga the manga edited.
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(manga: Manga, context: Context, data: Uri) {
        Observable
            .fromCallable {
                context.contentResolver.openInputStream(data)?.use {
                    if (manga.isLocal()) {
                        LocalSource.updateCover(context, manga, it)
                        manga.updateCoverLastModified(db)
                    } else if (manga.favorite) {
                        coverCache.setCustomCoverToCache(manga, it)
                        manga.updateCoverLastModified(db)
                    }
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ -> view.onSetCoverSuccess() },
                { view, e -> view.onSetCoverError(e) }
            )
    }

    fun deleteCustomCover(manga: Manga) {
        Observable
            .fromCallable {
                coverCache.deleteCustomCover(manga)
                manga.updateCoverLastModified(db)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ -> view.onSetCoverSuccess() },
                { view, e -> view.onSetCoverError(e) }
            )
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        // SY -->
        val isMergedSource = source is MergedSource
        val mergedIds = if (isMergedSource) mergedManga.mapNotNull { it.id } else emptyList()
        // SY <--
        observeDownloadsStatusSubscription?.let { remove(it) }
        observeDownloadsStatusSubscription = downloadManager.queue.getStatusObservable()
            .observeOn(Schedulers.io())
            .onBackpressureLatest()
            .filter { download -> /* SY --> */ if (isMergedSource) download.manga.id in mergedIds else /* SY <-- */ download.manga.id == manga.id }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(
                { view, it ->
                    onDownloadStatusChange(it)
                    view.onChapterDownloadUpdate(it)
                },
                { _, error ->
                    Timber.e(error)
                }
            )

        observeDownloadsPageSubscription?.let { remove(it) }
        observeDownloadsPageSubscription = downloadManager.queue.getProgressObservable()
            .observeOn(Schedulers.io())
            .onBackpressureLatest()
            .filter { download -> /* SY --> */ if (isMergedSource) download.manga.id in mergedIds else /* SY <-- */ download.manga.id == manga.id }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(MangaController::onChapterDownloadUpdate) { _, error ->
                Timber.e(error)
            }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): ChapterItem {
        // Create the model object.
        val model = ChapterItem(this, manga)

        // Find an active download for this chapter.
        val download = downloadManager.queue.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>) {
        // SY -->
        val isMergedSource = source is MergedSource
        // SY <--
        chapters
            .filter { downloadManager.isChapterDownloaded(/* SY --> */ if (isMergedSource) it.toMergedDownloadChapter() else it, if (isMergedSource) mergedManga.firstOrNull { manga -> it.manga_id == manga.id } ?: manga else /* SY <-- */ manga) }
            .forEach { it.status = Download.State.DOWNLOADED }
    }

    private fun Chapter.toMergedDownloadChapter() = Chapter.create().apply {
        url = this@toMergedDownloadChapter.url
        name = this@toMergedDownloadChapter.name
        id = this@toMergedDownloadChapter.id
        scanlator = this@toMergedDownloadChapter.scanlator?.substringAfter(": ")
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        hasRequested = true

        if (fetchChaptersJob?.isActive == true) return
        fetchChaptersJob = presenterScope.launchIO {
            try {
                if (source !is MergedSource) {
                    val chapters = source.getChapterList(manga.toMangaInfo())
                        .map { it.toSChapter() }

                    val (newChapters, _) = syncChaptersWithSource(db, chapters, manga, source)
                    if (manualFetch) {
                        downloadNewChapters(newChapters)
                    }
                } else {
                    source.fetchChaptersForMergedManga(manga, manualFetch, true, dedupe)
                }

                withUIContext { view?.onFetchChaptersDone() }
            } catch (e: Throwable) {
                withUIContext { view?.onFetchChaptersError(e) }
            }
        }
    }

    /**
     * Updates the UI after applying the filters.
     */
    private fun refreshChapters() {
        chaptersRelay.call(chapters)
    }

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapters the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapters: List<ChapterItem>): Observable<List<ChapterItem>> {
        var observable = Observable.from(chapters).subscribeOn(Schedulers.io())

        val unreadFilter = onlyUnread()
        if (unreadFilter == State.INCLUDE) {
            observable = observable.filter { !it.read }
        } else if (unreadFilter == State.EXCLUDE) {
            observable = observable.filter { it.read }
        }

        val downloadedFilter = onlyDownloaded()
        if (downloadedFilter == State.INCLUDE) {
            observable = observable.filter { it.isDownloaded || it.manga.isLocal() }
        } else if (downloadedFilter == State.EXCLUDE) {
            observable = observable.filter { !it.isDownloaded && !it.manga.isLocal() }
        }

        val bookmarkedFilter = onlyBookmarked()
        if (bookmarkedFilter == State.INCLUDE) {
            observable = observable.filter { it.bookmark }
        } else if (bookmarkedFilter == State.EXCLUDE) {
            observable = observable.filter { !it.bookmark }
        }

        // SY -->
        meta?.let { metadata ->
            metadata.filteredScanlators?.let { filteredScanlatorString ->
                val filteredScanlators = MdUtil.getScanlators(filteredScanlatorString)
                observable = observable.filter { it.scanlatorList().any { group -> filteredScanlators.contains(group) } }
            }
        }
        // SY <--

        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.SORTING_SOURCE -> when (sortDescending()) {
                true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
                false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
            }
            Manga.SORTING_NUMBER -> when (sortDescending()) {
                true -> { c1, c2 -> c2.chapter_number.compareTo(c1.chapter_number) }
                false -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
            }
            Manga.SORTING_UPLOAD_DATE -> when (sortDescending()) {
                true -> { c1, c2 -> c2.date_upload.compareTo(c1.date_upload) }
                false -> { c1, c2 -> c1.date_upload.compareTo(c2.date_upload) }
            }
            else -> throw NotImplementedError("Unimplemented sorting method")
        }

        return observable.toSortedList(sortFunction)
    }

    /**
     * Called when a download for the active manga changes status.
     * @param download the download whose status changed.
     */
    private fun onDownloadStatusChange(download: Download) {
        // Assign the download to the model object.
        if (download.status == Download.State.QUEUE) {
            chapters.find { it.id == download.chapter.id }?.let {
                if (it.download == null) {
                    it.download = download
                }
            }
        }

        // Force UI update if downloaded filter active and download finished.
        if (onlyDownloaded() != State.IGNORE && download.status == Download.State.DOWNLOADED) {
            refreshChapters()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? {
        return if (source.isEhBasedSource()) {
            val chapter = chapters.sortedBy { it.source_order }.getOrNull(0)
            if (chapter?.read == false) chapter else null
        } else {
            chapters.sortedByDescending { it.source_order }.find { !it.read }
        }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(selectedChapters: List<ChapterItem>, read: Boolean) {
        val chapters = selectedChapters.map { chapter ->
            chapter.read = read
            if (!read) {
                chapter.last_page_read = 0
            }
            chapter
        }

        launchIO {
            db.updateChaptersProgress(chapters).executeAsBlocking()

            if (preferences.removeAfterMarkedAsRead() /* SY --> */ && manga.shouldDeleteChapters(db, preferences) /* SY <-- */) {
                deleteChapters(chapters.filter { it.read })
            }
        }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<Chapter>) {
        // SY -->
        if (source is MergedSource) {
            chapters.groupBy { it.manga_id }.forEach { map ->
                val manga = mergedManga.firstOrNull { it.id == map.key } ?: return@forEach
                downloadManager.downloadChapters(manga, map.value.map { it.toMergedDownloadChapter() })
            }
        } else /* SY <-- */ downloadManager.downloadChapters(manga, chapters)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        launchIO {
            selectedChapters
                .forEach {
                    it.bookmark = bookmarked
                    db.updateChapterProgress(it).executeAsBlocking()
                }
        }
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>) {
        launchIO {
            try {
                downloadManager.deleteChapters(chapters, manga, source).forEach {
                    if (it is ChapterItem) {
                        it.status = Download.State.NOT_DOWNLOADED
                        it.download = null
                    }
                }

                if (onlyDownloaded() != State.IGNORE) {
                    refreshChapters()
                }

                view?.onChaptersDeleted(chapters)
            } catch (e: Throwable) {
                view?.onChaptersDeletedError(e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        if (chapters.isEmpty() || !manga.shouldDownloadNewChapters(db, preferences) || source.isEhBasedSource()) return

        downloadChapters(chapters)
    }

    /**
     * Reverses the sorting and requests an UI update.
     */
    fun reverseSortOrder() {
        manga.setChapterOrder(if (sortDescending()) Manga.SORT_ASC else Manga.SORT_DESC)
        db.updateFlags(manga).executeAsBlocking()
        refreshChapters()
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: State) {
        manga.readFilter = when (state) {
            State.IGNORE -> Manga.SHOW_ALL
            State.INCLUDE -> Manga.SHOW_UNREAD
            State.EXCLUDE -> Manga.SHOW_READ
        }
        db.updateFlags(manga).executeAsBlocking()
        refreshChapters()
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: State) {
        manga.downloadedFilter = when (state) {
            State.IGNORE -> Manga.SHOW_ALL
            State.INCLUDE -> Manga.SHOW_DOWNLOADED
            State.EXCLUDE -> Manga.SHOW_NOT_DOWNLOADED
        }
        db.updateFlags(manga).executeAsBlocking()
        refreshChapters()
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: State) {
        manga.bookmarkedFilter = when (state) {
            State.IGNORE -> Manga.SHOW_ALL
            State.INCLUDE -> Manga.SHOW_BOOKMARKED
            State.EXCLUDE -> Manga.SHOW_NOT_BOOKMARKED
        }
        db.updateFlags(manga).executeAsBlocking()
        refreshChapters()
    }

    // SY -->
    suspend fun setScanlatorFilter(filteredScanlators: Set<String>) {
        val meta = meta ?: return
        meta.filteredScanlators = if (filteredScanlators.size == allChapterScanlators.size) null else MdUtil.getScanlatorString(filteredScanlators)
        meta.flatten().let {
            db.insertFlatMetadataAsync(it).await()
        }
        refreshChapters()
    }
    // SY <--

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Int) {
        manga.displayMode = mode
        db.updateFlags(manga).executeAsBlocking()
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Int) {
        manga.sorting = sort
        db.updateFlags(manga).executeAsBlocking()
        refreshChapters()
    }

    /**
     * Whether downloaded only mode is enabled.
     */
    fun forceDownloaded(): Boolean {
        return manga.favorite && preferences.downloadedOnly().get()
    }

    /**
     * Whether the display only downloaded filter is enabled.
     */
    fun onlyDownloaded(): State {
        if (forceDownloaded()) {
            return State.INCLUDE
        }
        return when (manga.downloadedFilter) {
            Manga.SHOW_DOWNLOADED -> State.INCLUDE
            Manga.SHOW_NOT_DOWNLOADED -> State.EXCLUDE
            else -> State.IGNORE
        }
    }

    /**
     * Whether the display only downloaded filter is enabled.
     */
    fun onlyBookmarked(): State {
        return when (manga.bookmarkedFilter) {
            Manga.SHOW_BOOKMARKED -> State.INCLUDE
            Manga.SHOW_NOT_BOOKMARKED -> State.EXCLUDE
            else -> State.IGNORE
        }
    }

    /**
     * Whether the display only unread filter is enabled.
     */
    fun onlyUnread(): State {
        return when (manga.readFilter) {
            Manga.SHOW_UNREAD -> State.INCLUDE
            Manga.SHOW_READ -> State.EXCLUDE
            else -> State.IGNORE
        }
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending(): Boolean {
        return manga.sortDescending()
    }

    // Chapters list - end

    // Track sheet - start

    private fun fetchTrackers() {
        trackSubscription?.let { remove(it) }
        trackSubscription = db.getTracks(manga)
            .asRxObservable()
            .map { tracks ->
                loggedServices.map { service ->
                    TrackItem(tracks.find { it.sync_id == service.id }, service)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            // SY -->
            .map { trackItems ->
                if (manga.source in mangaDexSourceIds || mergedManga.any { it.source in mangaDexSourceIds }) {
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
        val mdManga = mergedManga.find { it.source in mangaDexSourceIds }
        val track = trackManager.mdList.createInitialTracker(manga, mdManga ?: manga)
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
                            }
                        }
                        .awaitAll()

                    withUIContext { view?.onTrackingRefreshDone() }
                } catch (e: Throwable) {
                    xLogD("Error registering tracking", e)
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
                xLogD("Error searching tracking", e)
                withUIContext { view?.onTrackingSearchResultsError(e) }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = manga.id!!
            launchIO {
                try {
                    service.bind(item)
                    db.insertTrack(item).executeAsBlocking()
                } catch (e: Throwable) {
                    xLogD("Error registering tracking", e)
                    withUIContext { view?.applicationContext?.toast(e.message) }
                }
            }
        } else {
            unregisterTracking(service)
        }
    }

    fun unregisterTracking(service: TrackService) {
        db.deleteTrackForManga(manga, service).executeAsBlocking()
    }

    private fun updateRemote(track: Track, service: TrackService) {
        launchIO {
            try {
                service.update(track)
                db.insertTrack(track).executeAsBlocking()
                withUIContext { view?.onTrackingRefreshDone() }
            } catch (e: Throwable) {
                xLogD("Error updating tracking", e)
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
            track.last_chapter_read = track.total_chapters
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
        track.last_chapter_read = chapterNumber
        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
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
