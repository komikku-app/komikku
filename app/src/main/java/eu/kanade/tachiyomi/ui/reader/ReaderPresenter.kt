package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import androidx.annotation.ColorInt
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.updateCoverLastModified
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.util.defaultReaderType
import exh.util.mangaType
import exh.util.shouldDeleteChapters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<ReaderActivity>() {

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    var manga: Manga? = null
        private set

    // SY -->
    var meta: RaisedSearchMetadata? = null
        private set
    // SY <--

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = -1L

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * Subscription to prevent setting chapters as active from multiple threads.
     */
    private var activeChapterSubscription: Subscription? = null

    /**
     * Relay for currently active viewer chapters.
     */
    /* [EXH] private */ val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()

    /**
     * Relay used when loading prev/next chapter needed to lock the UI (with a dialog).
     */
    private val isLoadingAdjacentChapterRelay = BehaviorRelay.create<Boolean>()

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList by lazy {
        val manga = manga!!
        // SY -->
        val filteredScanlators = manga.filtered_scanlators?.let { MdUtil.getScanlators(it) }
        // SY <--
        val dbChapters = /* SY --> */ if (manga.source == MERGED_SOURCE_ID) {
            (sourceManager.get(MERGED_SOURCE_ID) as MergedSource)
                .getChaptersAsBlocking(manga)
        } else /* SY <-- */ db.getChapters(manga).executeAsBlocking()

        val selectedChapter = dbChapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (preferences.skipRead() || preferences.skipFiltered()) -> {
                val list = dbChapters.filterNot {
                    when {
                        preferences.skipRead() && it.read -> true
                        preferences.skipFiltered() -> {
                            (manga.readFilter == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (manga.readFilter == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                (manga.downloadedFilter == Manga.CHAPTER_SHOW_DOWNLOADED && !downloadManager.isChapterDownloaded(it, manga)) ||
                                (manga.downloadedFilter == Manga.CHAPTER_SHOW_NOT_DOWNLOADED && downloadManager.isChapterDownloaded(it, manga)) ||
                                (manga.bookmarkedFilter == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                // SY -->
                                (filteredScanlators != null && MdUtil.getScanlators(it.scanlator).none { group -> filteredScanlators.contains(group) })
                            // SY <--
                        }
                        else -> false
                    }
                }
                    .toMutableList()

                val find = list.find { it.id == chapterId }
                if (find == null) {
                    list.add(selectedChapter)
                }
                list
            }
            else -> dbChapters
        }

        chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .map(::ReaderChapter)
    }

    private var hasTrackers: Boolean = false
    private val checkTrackers: (Manga) -> Unit = { manga ->
        val tracks = db.getTracks(manga).executeAsBlocking()

        hasTrackers = tracks.size > 0
    }

    private val incognitoMode = preferences.incognitoMode().get()

    /**
     * Called when the presenter is created. It retrieves the saved active chapter if the process
     * was restored.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (savedState != null) {
            chapterId = savedState.getLong(::chapterId.name, -1)
        }
    }

    /**
     * Called when the presenter is destroyed. It saves the current progress and cleans up
     * references on the currently active chapters.
     */
    override fun onDestroy() {
        super.onDestroy()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveChapterProgress(currentChapters.currChapter)
            saveChapterHistory(currentChapters.currChapter)
        }
    }

    /**
     * Called when the presenter instance is being saved. It saves the currently active chapter
     * id and the last page read.
     */
    override fun onSave(state: Bundle) {
        super.onSave(state)
        val currentChapter = getCurrentChapter()
        if (currentChapter != null) {
            currentChapter.requestedPage = currentChapter.chapter.last_page_read
            state.putLong(::chapterId.name, currentChapter.chapter.id!!)
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onBackPressed() {
        deletePendingChapters()
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentChapter = getCurrentChapter() ?: return
        saveChapterProgress(currentChapter)
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    fun init(mangaId: Long, initialChapterId: Long) {
        if (!needsInit()) return

        db.getManga(mangaId).asRxObservable()
            .first()
            .observeOn(AndroidSchedulers.mainThread())
            // SY -->
            .flatMap { manga ->
                val source = sourceManager.get(manga.source)?.getMainSource()
                if (manga.initialized && source is MetadataSource<*, *>) {
                    db.getFlatMetadataForManga(mangaId).asRxSingle().map {
                        manga to it?.raise(source.metaClass)
                    }.toObservable()
                } else {
                    Observable.just(manga to null)
                }
            }
            .doOnNext { init(it.first, initialChapterId, it.second) }
            // SY <--
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                ReaderActivity::setInitialChapterError
            )
    }

    /**
     * Initializes this presenter with the given [manga] and [initialChapterId]. This method will
     * set the chapter loader, view subscriptions and trigger an initial load.
     */
    private fun init(manga: Manga, initialChapterId: Long /* SY --> */, metadata: RaisedSearchMetadata?/* SY <-- */) {
        if (!needsInit()) return

        this.manga = manga
        // SY -->
        this.meta = metadata
        // SY <--
        if (chapterId == -1L) chapterId = initialChapterId

        checkTrackers(manga)

        val context = Injekt.get<Application>()
        val source = sourceManager.getOrStub(manga.source)
        val mergedReferences = if (source is MergedSource) db.getMergedMangaReferences(manga.id!!).executeAsBlocking() else emptyList()
        val mergedManga = if (source is MergedSource) db.getMergedMangas(manga.id!!).executeAsBlocking() else emptyList()
        loader = ChapterLoader(context, downloadManager, manga, source, sourceManager, mergedReferences, mergedManga)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)
        isLoadingAdjacentChapterRelay.subscribeLatestCache(ReaderActivity::setProgressDialog)

        // Read chapterList from an io thread because it's retrieved lazily and would block main.
        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = Observable
            .fromCallable { chapterList.first { chapterId == it.chapter.id } }
            .flatMap { getLoadObservable(loader!!, it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                ReaderActivity::setInitialChapterError
            )
    }

    // SY -->
    suspend fun getChapters(context: Context): List<ReaderChapterItem> {
        return withIOContext {
            val currentChapter = getCurrentChapter()
            val decimalFormat = DecimalFormat(
                "#.###",
                DecimalFormatSymbols()
                    .apply { decimalSeparator = '.' }
            )

            chapterList.map {
                ReaderChapterItem(
                    it.chapter,
                    manga!!,
                    it.chapter == currentChapter?.chapter,
                    context,
                    preferences.dateFormat(),
                    decimalFormat
                )
            }
        }
    }
    // SY <--

    /**
     * Returns an observable that loads the given [chapter] with this [loader]. This observable
     * handles main thread synchronization and updating the currently active chapters on
     * [viewerChaptersRelay], however callers must ensure there won't be more than one
     * subscription active by unsubscribing any existing [activeChapterSubscription] before.
     * Callers must also handle the onError event.
     */
    private fun getLoadObservable(
        loader: ChapterLoader,
        chapter: ReaderChapter
    ): Observable<ViewerChapters> {
        return loader.loadChapter(chapter)
            .andThen(
                Observable.fromCallable {
                    val chapterPos = chapterList.indexOf(chapter)

                    ViewerChapters(
                        chapter,
                        chapterList.getOrNull(chapterPos - 1),
                        chapterList.getOrNull(chapterPos + 1)
                    )
                }
            )
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { newChapters ->
                val oldChapters = viewerChaptersRelay.value

                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                oldChapters?.unref()

                viewerChaptersRelay.call(newChapters)
            }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        Timber.d("Loading ${chapter.chapter.url}")

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .toCompletable()
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    fun loadNewChapterFromSheet(chapter: Chapter) {
        val newChapter = chapterList.firstOrNull { it.chapter.id == chapter.id } ?: return
        loadAdjacent(newChapter)
    }

    /**
     * Called when the user is going to load the prev/next chapter through the menu button. It
     * sets the [isLoadingAdjacentChapterRelay] that the view uses to prevent any further
     * interaction until the chapter is loaded.
     */
    private fun loadAdjacent(chapter: ReaderChapter) {
        val loader = loader ?: return

        Timber.d("Loading adjacent ${chapter.chapter.url}")

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .doOnSubscribe { isLoadingAdjacentChapterRelay.call(true) }
            .doOnUnsubscribe { isLoadingAdjacentChapterRelay.call(false) }
            .subscribeFirst(
                { view, _ ->
                    view.moveToPageIndex(0)
                },
                { _, _ ->
                    // Ignore onError event, viewers handle that state
                }
            )
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        Timber.d("Preloading ${chapter.chapter.url}")

        val loader = loader ?: return

        loader.loadChapter(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            // Update current chapters whenever a chapter is preloaded
            .doOnCompleted { viewerChaptersRelay.value?.let(viewerChaptersRelay::call) }
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean) {
        val currentChapters = viewerChaptersRelay.value ?: return

        val selectedChapter = page.chapter

        // Insert page doesn't change page progress
        if (page is InsertPage) {
            return
        }

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        val shouldTrack = !incognitoMode || hasTrackers
        if (
            (selectedChapter.pages?.lastIndex == page.index && shouldTrack) ||
            (hasExtraPage && selectedChapter.pages?.lastIndex?.minus(1) == page.index && shouldTrack)
        ) {
            selectedChapter.chapter.read = true
            // SY -->
            if (manga?.isEhBasedManga() == true) {
                chapterList
                    .filter { it.chapter.source_order > selectedChapter.chapter.source_order }
                    .onEach {
                        it.chapter.read = true
                        saveChapterProgress(it)
                    }
            }
            // SY <--
            updateTrackChapterRead(selectedChapter)
            deleteChapterIfNeeded(selectedChapter)
            deleteChapterFromDownloadQueue(currentChapters.currChapter)
        }

        if (selectedChapter != currentChapters.currChapter) {
            Timber.d("Setting ${selectedChapter.chapter.url} as active")
            onChapterChanged(currentChapters.currChapter)
            loadNewChapter(selectedChapter)
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun deleteChapterFromDownloadQueue(currentChapter: ReaderChapter) {
        downloadManager.getChapterDownloadOrNull(currentChapter.chapter)?.let { download ->
            downloadManager.deletePendingDownload(download)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val removeAfterReadSlots = preferences.removeAfterReadSlots()
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)
        // Check if deleting option is enabled and chapter exists
        // SY -->
        val manga = manga
        // SY <--
        if (removeAfterReadSlots != -1 && chapterToDelete != null /* SY --> */ && (manga == null || manga.shouldDeleteChapters(db, preferences)) /* SY <-- */) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    /**
     * Called when a chapter changed from [fromChapter] to [toChapter]. It updates [fromChapter]
     * on the database.
     */
    private fun onChapterChanged(fromChapter: ReaderChapter) {
        saveChapterProgress(fromChapter)
        saveChapterHistory(fromChapter)
    }

    /**
     * Saves this [chapter] progress (last read page and whether it's read).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private fun saveChapterProgress(chapter: ReaderChapter) {
        if (!incognitoMode || hasTrackers) {
            db.updateChapterProgress(chapter.chapter).asRxCompletable()
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    /**
     * Saves this [chapter] last read history if incognito mode isn't on.
     */
    private fun saveChapterHistory(chapter: ReaderChapter) {
        if (!incognitoMode) {
            val history = History.create(chapter.chapter).apply { last_read = Date().time }
            db.updateHistoryLastRead(history).asRxCompletable()
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    /**
     * Called from the activity to preload the given [chapter].
     */
    fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    fun loadNextChapter() {
        val nextChapter = viewerChaptersRelay.value?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    fun loadPreviousChapter() {
        val prevChapter = viewerChaptersRelay.value?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun bookmarkCurrentChapter(bookmarked: Boolean) {
        if (getCurrentChapter()?.chapter == null) {
            return
        }

        val chapter = getCurrentChapter()?.chapter!!
        chapter.bookmark = bookmarked
        db.updateChapterProgress(chapter).executeAsBlocking()
    }

    // SY -->
    fun toggleBookmark(chapter: Chapter) {
        chapter.bookmark = !chapter.bookmark
        db.updateChapterProgress(chapter).executeAsBlocking()
        chapterList.firstOrNull { it.chapter.id == chapter.id }?.let { it.chapter.bookmark == !chapter.bookmark }
    }
    // SY <--

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = preferences.defaultReadingMode()
        val manga = manga ?: return default
        val readingMode = ReadingModeType.fromPreference(manga.readingModeType)
        // SY -->
        return when {
            resolveDefault && readingMode == ReadingModeType.DEFAULT && preferences.useAutoWebtoon().get() -> {
                manga.defaultReaderType(manga.mangaType(sourceName = sourceManager.get(manga.source)?.name)) ?: if (manga.readingModeType == ReadingModeType.DEFAULT.flagValue) {
                    default
                } else {
                    readingMode.prefValue
                }
            }
            resolveDefault && readingMode == ReadingModeType.DEFAULT -> default
            else -> manga.readingModeType
        }
        // SY <--
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingModeType: Int) {
        val manga = manga ?: return
        manga.readingModeType = readingModeType
        db.updateViewerFlags(manga).executeAsBlocking()

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    // Save current page
                    val currChapter = currChapters.currChapter
                    currChapter.requestedPage = currChapter.chapter.last_page_read

                    // Emit manga and chapters to the new viewer
                    view.setManga(manga)
                    view.setChapters(currChapters)
                }
            })
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientationType(): Int {
        val default = preferences.defaultOrientationType()
        return when (manga?.orientationType) {
            OrientationType.DEFAULT.flagValue -> default
            else -> manga?.orientationType ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(rotationType: Int) {
        val manga = manga ?: return
        manga.orientationType = rotationType
        db.updateViewerFlags(manga).executeAsBlocking()

        Timber.i("Manga orientation is ${manga.orientationType}")

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    view.setOrientation(getMangaOrientationType())
                }
            })
    }

    /**
     * Saves the image of this [page] in the given [directory] and returns the file location.
     */
    private fun saveImage(page: ReaderPage, directory: File, manga: Manga): File {
        val stream = page.stream!!
        val type = ImageUtil.findImageType(stream) ?: throw Exception("Not an image")

        directory.mkdirs()

        val chapter = page.chapter.chapter

        // Build destination file.
        val filenameSuffix = " - ${page.number}.${type.extension}"
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".takeBytes(MAX_FILE_NAME_BYTES - filenameSuffix.byteSize())
        ) + filenameSuffix

        val destFile = File(directory, filename)
        stream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * Saves the image of this [page] on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        // Pictures directory.
        val baseDir = Environment.getExternalStorageDirectory().absolutePath +
            File.separator + Environment.DIRECTORY_PICTURES +
            File.separator + context.getString(R.string.app_name)
        val destDir = if (preferences.folderPerManga()) {
            File(baseDir + File.separator + manga.title)
        } else {
            File(baseDir)
        }

        // Copy file in background.
        Observable.fromCallable { saveImage(page, destDir, manga) }
            .doOnNext { file ->
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
            }
            .doOnError { notifier.onError(it.message) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onSaveImageResult(SaveImageResult.Success(file)) },
                { view, error -> view.onSaveImageResult(SaveImageResult.Error(error)) }
            )
    }

    // SY -->
    fun saveImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        if (firstPage.status != Page.READY) return
        if (secondPage.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        // Pictures directory.
        val destDir = File(
            Environment.getExternalStorageDirectory().absolutePath +
                File.separator + Environment.DIRECTORY_PICTURES +
                File.separator + context.getString(R.string.app_name)
        )

        // Copy file in background.
        Observable.fromCallable { saveImages(firstPage, secondPage, isLTR, bg, destDir, manga) }
            .doOnNext { file ->
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
            }
            .doOnError { notifier.onError(it.message) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onSaveImageResult(SaveImageResult.Success(file)) },
                { view, error -> view.onSaveImageResult(SaveImageResult.Error(error)) }
            )
    }

    private fun saveImages(page1: ReaderPage, page2: ReaderPage, isLTR: Boolean, @ColorInt bg: Int, directory: File, manga: Manga): File {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBytes = stream1().readBytes()
        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val imageBytes2 = stream2().readBytes()
        val imageBitmap2 = BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)

        val stream = ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg)
        directory.mkdirs()

        val chapter = page1.chapter.chapter

        // Build destination file.
        val filenameSuffix = " - ${page1.number}-${page2.number}.jpg"
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".takeBytes(MAX_FILE_NAME_BYTES - filenameSuffix.byteSize())
        ) + filenameSuffix

        val destFile = File(directory, filename)
        stream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }
    // SY <--

    /**
     * Shares the image of this [page] and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompresssed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        Observable.fromCallable { destDir.deleteRecursively() } // Keep only the last shared file
            .map { saveImage(page, destDir, manga) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onShareImageResult(file, page) },
                { _, _ -> /* Empty */ }
            )
    }

    // SY -->
    fun shareImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        if (firstPage.status != Page.READY) return
        if (secondPage.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        Observable.fromCallable { destDir.deleteRecursively() } // Keep only the last shared file
            .map { saveImages(firstPage, secondPage, isLTR, bg, destDir, manga) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onShareImageResult(file, firstPage, secondPage) },
                { _, _ -> /* Empty */ }
            )
    }
    // SY <--

    /**
     * Sets the image of this [page] as cover and notifies the UI of the result.
     */
    fun setAsCover(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        Observable
            .fromCallable {
                if (manga.isLocal()) {
                    val context = Injekt.get<Application>()
                    LocalSource.updateCover(context, manga, stream())
                    manga.updateCoverLastModified(db)
                    R.string.cover_updated
                    SetAsCoverResult.Success
                } else {
                    if (manga.favorite) {
                        coverCache.setCustomCoverToCache(manga, stream())
                        manga.updateCoverLastModified(db)
                        coverCache.clearMemoryCache()
                        SetAsCoverResult.Success
                    } else {
                        SetAsCoverResult.AddToLibraryFirst
                    }
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, result -> view.onSetAsCoverResult(result) },
                { view, _ -> view.onSetAsCoverResult(SetAsCoverResult.Error) }
            )
    }

    /**
     * Results of the set as cover feature.
     */
    enum class SetAsCoverResult {
        Success, AddToLibraryFirst, Error
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val file: File) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (!preferences.autoUpdateTrack()) return
        val manga = manga ?: return

        val chapterRead = readerChapter.chapter.chapter_number.toInt()

        val trackManager = Injekt.get<TrackManager>()

        launchIO {
            db.getTracks(manga).executeAsBlocking()
                .mapNotNull { track ->
                    val service = trackManager.getService(track.sync_id)
                    if (service != null && service.isLogged && chapterRead > track.last_chapter_read /* SY --> */ && ((service.id == TrackManager.MDLIST && track.status != FollowStatus.UNFOLLOWED.int) || service.id != TrackManager.MDLIST)/* SY <-- */) {
                        track.last_chapter_read = chapterRead

                        // We want these to execute even if the presenter is destroyed and leaks
                        // for a while. The view can still be garbage collected.
                        async {
                            runCatching {
                                service.update(track)
                                db.insertTrack(track).executeAsBlocking()
                            }
                        }
                    } else {
                        null
                    }
                }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { Timber.w(it) }
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        launchIO {
            downloadManager.enqueueDeleteChapters(listOf(chapter.chapter), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        launchIO {
            downloadManager.deletePendingChapters()
        }
    }

    companion object {
        // Safe theoretical max filename size is 255 bytes and 1 char = 2-4 bytes (UTF-8)
        private const val MAX_FILE_NAME_BYTES = 250
    }
}
