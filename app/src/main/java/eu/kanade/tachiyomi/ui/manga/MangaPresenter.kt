package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.elvishew.xlog.XLog
import com.jakewharton.rxrelay.BehaviorRelay
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.util.chapter.ChapterSettingsHelper
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.isNullOrUnsubscribed
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.updateCoverLastModified
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import exh.MERGED_SOURCE_ID
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.isEhBasedSource
import exh.md.utils.FollowStatus
import exh.merged.sql.models.MergedMangaReference
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.EnhancedHttpSource.Companion.getMainSource
import exh.util.asObservable
import exh.util.await
import exh.util.shouldDeleteChapters
import exh.util.trimOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
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
    private var fetchMangaSubscription: Subscription? = null

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
    private var fetchChaptersSubscription: Subscription? = null

    /**
     * Subscription to observe download status changes.
     */
    private var observeDownloadsSubscription: Subscription? = null

    // EXH -->
    private val customMangaManager: CustomMangaManager by injectLazy()

    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    private val redirectUserRelay = BehaviorRelay.create<EXHRedirect>()

    data class EXHRedirect(val manga: Manga, val update: Boolean)

    var meta: RaisedSearchMetadata? = null

    private var mergedManga = emptyList<Manga>()

    var dedupe: Boolean = true
    // EXH <--

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // SY -->
        if (manga.initialized && source.getMainSource() is MetadataSource<*, *>) {
            getMangaMetaObservable().subscribeLatestCache({ view, flatMetadata -> if (flatMetadata != null) view.onNextMetaInfo(flatMetadata) else XLog.d("Invalid metadata") })
        }

        if (source is MergedSource) {
            launchIO { mergedManga = db.getMergedMangas(manga.id!!).await() }
        }
        // SY <--

        if (!manga.favorite) {
            ChapterSettingsHelper.applySettingDefaults(manga)
        }

        // Manga info - start

        getMangaObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache({ view, manga -> view.onNextMangaInfo(manga, source) })

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
            (/* SY --> */if (source is MergedSource) source.getChaptersFromDB(manga, true, dedupe).asObservable() else /* SY <-- */ db.getChapters(manga).asRxObservable())
                .map { chapters ->
                    // Convert every chapter to a model.
                    chapters.map { it.toModel() }
                }
                .doOnNext { chapters ->
                    // Find downloaded chapters
                    setDownloadedChapters(chapters)

                    // Store the last emission
                    this.chapters = chapters

                    // Listen for download status changes
                    observeDownloads()

                    // SY -->
                    if (chapters.isNotEmpty() && (source.isEhBasedSource()) && DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled) {
                        // Check for gallery in library and accept manga with lowest id
                        // Find chapters sharing same root
                        add(
                            updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)
                                .subscribeOn(Schedulers.io())
                                .subscribe { (acceptedChain, _) ->
                                    // Redirect if we are not the accepted root
                                    if (manga.id != acceptedChain.manga.id) {
                                        // Update if any of our chapters are not in accepted manga's chapters
                                        val ourChapterUrls = chapters.map { it.url }.toSet()
                                        val acceptedChapterUrls = acceptedChain.chapters.map { it.url }.toSet()
                                        val update = (ourChapterUrls - acceptedChapterUrls).isNotEmpty()
                                        redirectUserRelay.call(
                                            EXHRedirect(
                                                acceptedChain.manga,
                                                update
                                            )
                                        )
                                    }
                                }
                        )
                    }
                    // SY <--
                }
                .subscribe { chaptersRelay.call(it) }
        )

        // Chapters list - end
    }

    // Manga info - start

    private fun getMangaObservable(): Observable<Manga> {
        return db.getManga(manga.url, manga.source).asRxObservable()
    }

    private fun getTrackingObservable(): Observable<Int> {
        // SY -->
        val sourceIsMangaDex = source.getMainSource() is MangaDex
        // SY <--
        if (!trackManager.hasLoggedServices(/* SY --> */sourceIsMangaDex/* SY <-- */)) {
            return Observable.just(0)
        }

        return db.getTracks(manga).asRxObservable()
            .map { tracks ->
                val loggedServices = trackManager.services.filter { it.isLogged /* SY --> */ && ((it.id == TrackManager.MDLIST && sourceIsMangaDex) || it.id != TrackManager.MDLIST) /* SY <-- */ }.map { it.id }
                tracks
                    // SY -->
                    .filterNot { it.sync_id == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int }
                    // SY <--
                    .filter { it.sync_id in loggedServices }
            }
            .map { it.size }
    }

    // SY -->
    private fun getMangaMetaObservable(): Observable<FlatMetadata?> {
        val mangaId = manga.id
        return if (mangaId != null) {
            db.getFlatMetadataForManga(mangaId).asRxObservable()
                .observeOn(AndroidSchedulers.mainThread())
        } else Observable.just(null)
    }
    // SY <--

    /**
     * Fetch manga information from source.
     */
    fun fetchMangaFromSource(manualFetch: Boolean = false) {
        if (!fetchMangaSubscription.isNullOrUnsubscribed()) return
        fetchMangaSubscription = Observable.defer { source.fetchMangaDetails(manga) }
            .map { networkManga ->
                manga.prepUpdateCover(coverCache, networkManga, manualFetch)
                manga.copyFrom(networkManga)
                manga.initialized = true
                db.insertManga(manga).executeAsBlocking()
                manga
            }
            // SY -->
            .doOnNext {
                if (source.getMainSource() is MetadataSource<*, *>) {
                    getMangaMetaObservable().subscribeLatestCache({ view, flatMetadata -> if (flatMetadata != null) view.onNextMetaInfo(flatMetadata) else XLog.d("Invalid metadata") })
                }
            }
            // SY <--
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ ->
                    view.onFetchMangaInfoDone()
                },
                MangaController::onFetchMangaInfoError
            )
    }

    // SY -->
    fun updateMangaInfo(
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        tags: List<String>?,
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
            LocalSource(downloadManager.context).updateMangaInfo(manga)
            db.updateMangaInfo(manga).executeAsBlocking()
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags.joinToString() != manga.genre) {
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
                genre
            )
            customMangaManager.saveMangaInfo(manga)
        }

        if (uri != null) {
            editCoverWithStream(uri)
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

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.source == LocalSource.ID) {
            LocalSource.updateCover(downloadManager.context, manga, inputStream)
            manga.updateCoverLastModified(db)
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
        val originalManga = db.getManga(originalMangaId).await() ?: throw IllegalArgumentException("Unknown manga ID: $originalMangaId")
        if (originalManga.source == MERGED_SOURCE_ID) {
            val children = db.getMergedMangaReferences(originalMangaId).await()
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

            db.insertMergedMangas(mangaReferences).await()

            return originalManga
        } else {
            val mergedManga = Manga.create(originalManga.url, originalManga.title, MERGED_SOURCE_ID).apply {
                copyFrom(originalManga)
                favorite = true
                last_update = originalManga.last_update
                viewer = originalManga.viewer
                chapter_flags = originalManga.chapter_flags
                sorting = Manga.SORTING_NUMBER
                date_added = Date().time
            }
            var existingManga = db.getManga(mergedManga.url, mergedManga.source).await()
            while (existingManga != null) {
                if (existingManga.favorite) {
                    throw IllegalArgumentException("This merged manga is a duplicate!")
                } else if (!existingManga.favorite) {
                    withContext(NonCancellable) {
                        db.deleteManga(existingManga!!).await()
                        db.deleteMangaForMergedManga(existingManga!!.id!!).await()
                    }
                }
                existingManga = db.getManga(mergedManga.url, mergedManga.source).await()
            }

            // Reload chapters immediately
            mergedManga.initialized = false

            val newId = db.insertManga(mergedManga).await().insertedId()
            if (newId != null) mergedManga.id = newId

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

            db.insertMergedMangas(listOf(originalMangaReference, newMangaReference, mergedMangaReference)).await()

            return mergedManga
        }

        // Note that if the manga are merged in a different order, this won't trigger, but I don't care lol
    }

    fun updateMergeSettings(mergeReference: MergedMangaReference?, mergedMangaReferences: List<MergedMangaReference>) {
        launchIO {
            mergeReference?.let {
                db.updateMergeMangaSettings(it).await()
            }
            if (mergedMangaReferences.isNotEmpty()) db.updateMergedMangaSettings(mergedMangaReferences).await()
        }
    }

    fun toggleDedupe() {
        // I cant find any way to call the chapter list subscription to get the chapters again
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
        observeDownloadsSubscription?.let { remove(it) }
        observeDownloadsSubscription = downloadManager.queue.getStatusObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .filter { download -> /* SY --> */ if (isMergedSource) download.manga.id in mergedIds else /* SY <-- */ download.manga.id == manga.id }
            .doOnNext { onDownloadStatusChange(it) }
            .subscribeLatestCache(MangaController::onChapterStatusChange) { _, error ->
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
            .filter { downloadManager.isChapterDownloaded(it, /* SY --> */ if (isMergedSource) mergedManga.firstOrNull { manga -> it.manga_id == manga.id } ?: manga else /* SY <-- */ manga) }
            .forEach { it.status = Download.DOWNLOADED }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        hasRequested = true

        if (!fetchChaptersSubscription.isNullOrUnsubscribed()) return
        fetchChaptersSubscription = /* SY --> */ if (source !is MergedSource) {
            // SY <--
            Observable.defer { source.fetchChapterList(manga) }
                .subscribeOn(Schedulers.io())
                .map { syncChaptersWithSource(db, it, manga, source) }
                .doOnNext {
                    if (manualFetch) {
                        downloadNewChapters(it.first)
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeFirst(
                    { view, _ ->
                        view.onFetchChaptersDone()
                    },
                    MangaController::onFetchChaptersError
                )
            // SY -->
        } else {
            Observable.defer { source.fetchChaptersForMergedManga(manga, manualFetch, true, dedupe).asObservable() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                }
                .subscribeFirst(
                    { view, _ ->
                        view.onFetchChaptersDone()
                    },
                    MangaController::onFetchChaptersError
                )
        }
        // SY <--
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
        if (download.status == Download.QUEUE) {
            chapters.find { it.id == download.chapter.id }?.let {
                if (it.download == null) {
                    it.download = download
                }
            }
        }

        // Force UI update if downloaded filter active and download finished.
        if (onlyDownloaded() != State.IGNORE && download.status == Download.DOWNLOADED) {
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
                deleteChapters(chapters)
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
                downloadManager.downloadChapters(manga, map.value)
            }
        } else /* SY <-- */ downloadManager.downloadChapters(manga, chapters)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        Observable.from(selectedChapters)
            .doOnNext { chapter ->
                chapter.bookmark = bookmarked
            }
            .toList()
            .flatMap { db.updateChaptersProgress(it).asRxObservable() }
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>) {
        Observable.just(chapters)
            .doOnNext { deleteChaptersInternal(chapters) }
            .doOnNext { if (onlyDownloaded() != State.IGNORE) refreshChapters() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ ->
                    view.onChaptersDeleted(chapters)
                },
                MangaController::onChaptersDeletedError
            )
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        if (chapters.isEmpty() || !manga.shouldDownloadNewChapters(db, preferences) || source.isEhBasedSource()) return

        downloadChapters(chapters)
    }

    /**
     * Deletes a list of chapters from disk. This method is called in a background thread.
     * @param chapters the chapters to delete.
     */
    private fun deleteChaptersInternal(chapters: List<ChapterItem>) {
        downloadManager.deleteChapters(chapters, manga, source).forEach {
            if (it is ChapterItem) {
                it.status = Download.NOT_DOWNLOADED
                it.download = null
            }
        }
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
}
