package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.copyFrom
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import exh.source.MERGED_SOURCE_ID
import exh.util.nullIfBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.GetMergedAnimeForDownloading
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.toAnimeUpdate
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val updateAnime: UpdateAnime = Injekt.get()
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get()
    private val fetchInterval: FetchInterval = Injekt.get()
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get()

    // SY -->
    private val getFavorites: GetFavorites = Injekt.get()
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()
    private val getMergedAnimeForDownloading: GetMergedAnimeForDownloading = Injekt.get()
    private val getTracks: GetTracks = Injekt.get()
    private val insertTrack: InsertTrack = Injekt.get()
    private val trackerManager: TrackerManager = Injekt.get()
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get()
    private val setSeenStatus: SetSeenStatus = Injekt.get()
    // SY <--

    private val notifier = LibraryUpdateNotifier(context)

    // KMK -->
    private val libraryUpdateStatus: LibraryUpdateStatus = Injekt.get()
    private val deleteLibraryUpdateErrors: DeleteLibraryUpdateErrors = Injekt.get()
    private val insertLibraryUpdateErrors: InsertLibraryUpdateErrors = Injekt.get()
    private val insertLibraryUpdateErrorMessages: InsertLibraryUpdateErrorMessages = Injekt.get()
    // KMK <--

    private var mangaToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = Injekt.get<LibraryPreferences>()
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        // KMK -->
        libraryUpdateStatus.start()

        deleteLibraryUpdateErrors.cleanUnrelevantAnimeErrors()
        // KMK <--

        setForegroundSafely()

        val target = inputData.getString(KEY_TARGET)?.let { Target.valueOf(it) } ?: Target.CHAPTERS

        // If this is a episode update, set the last update time to now
        if (target == Target.CHAPTERS) {
            libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())
        }

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        // SY -->
        val group = inputData.getInt(KEY_GROUP, LibraryGroup.BY_DEFAULT)
        val groupExtra = inputData.getString(KEY_GROUP_EXTRA)
        // SY <--
        addMangaToQueue(categoryId, group, groupExtra)

        return withIOContext {
            try {
                when (target) {
                    Target.CHAPTERS -> updateEpisodeList()
                    Target.COVERS -> updateCovers()
                }
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
                // KMK -->
                libraryUpdateStatus.stop()
                // KMK <--
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = LibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Adds list of manga to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addMangaToQueue(categoryId: Long, group: Int, groupExtra: String?) {
        val libraryManga = getLibraryAnime.await()
        // SY -->
        val groupLibraryUpdateType = libraryPreferences.groupLibraryUpdateType().get()
        // SY <--

        val listToUpdate = if (categoryId != -1L) {
            libraryManga.filter { it.category == categoryId }
        } else if (
            group == LibraryGroup.BY_DEFAULT ||
            groupLibraryUpdateType == GroupLibraryMode.GLOBAL ||
            (groupLibraryUpdateType == GroupLibraryMode.ALL_BUT_UNGROUPED && group == LibraryGroup.UNGROUPED)
        ) {
            val categoriesToUpdate = libraryPreferences.updateCategories().get().map(String::toLong)
            val includedManga = if (categoriesToUpdate.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToUpdate }
            } else {
                libraryManga
            }

            val categoriesToExclude = libraryPreferences.updateCategoriesExclude().get().map { it.toLong() }
            val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToExclude }.map { it.anime.id }
            } else {
                emptyList()
            }

            includedManga
                .filterNot { it.anime.id in excludedMangaIds }
        } else {
            when (group) {
                LibraryGroup.BY_TRACK_STATUS -> {
                    val trackingExtra = groupExtra?.toIntOrNull() ?: -1
                    val tracks = getTracks.await().groupBy { it.animeId }

                    libraryManga.filter { (manga) ->
                        val status = tracks[manga.id]?.firstNotNullOfOrNull { track ->
                            TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                        } ?: TrackStatus.OTHER
                        status.int == trackingExtra
                    }
                }

                LibraryGroup.BY_SOURCE -> {
                    val sourceExtra = groupExtra?.nullIfBlank()?.toIntOrNull()
                    val source = libraryManga.map { it.anime.source }
                        .distinct()
                        .sorted()
                        .getOrNull(sourceExtra ?: -1)

                    if (source != null) libraryManga.filter { it.anime.source == source } else emptyList()
                }

                LibraryGroup.BY_STATUS -> {
                    val statusExtra = groupExtra?.toLongOrNull() ?: -1
                    libraryManga.filter {
                        it.anime.status == statusExtra
                    }
                }

                LibraryGroup.UNGROUPED -> libraryManga
                else -> libraryManga
            }
            // SY <--
        }

        val restrictions = libraryPreferences.autoUpdateAnimeRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Anime, String?>>()
        val (_, fetchWindowUpperBound) = fetchInterval.getWindow(ZonedDateTime.now())

        mangaToUpdate = listToUpdate
            // SY -->
            .distinctBy { it.anime.id }
            // SY <--
            .filter {
                when {
                    it.anime.updateStrategy != UpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.anime to
                                context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ANIME_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> {
                        skippedUpdates.add(it.anime to context.stringResource(MR.strings.skipped_reason_completed))
                        false
                    }

                    ANIME_HAS_UNSEEN in restrictions && it.unseenCount != 0L -> {
                        skippedUpdates.add(it.anime to context.stringResource(MR.strings.skipped_reason_not_caught_up))
                        false
                    }

                    ANIME_NON_SEEN in restrictions && it.totalEpisodes > 0L && !it.hasStarted -> {
                        skippedUpdates.add(it.anime to context.stringResource(MR.strings.skipped_reason_not_started))
                        false
                    }

                    ANIME_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.anime to
                                context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }

                    else -> true
                }
            }
            .sortedBy { it.anime.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) ->
                        "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]"
                    }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates manga in [mangaToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateAnime] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateEpisodeList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()
        val newUpdates = CopyOnWriteArrayList<Pair<Anime, Array<Episode>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Anime, String?>>()
        val hasDownloads = AtomicBoolean(false)

        val fetchWindow = fetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            mangaToUpdate.groupBy { it.anime.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.anime
                                ensureActive()

                                // Don't continue to update if manga is not in library
                                if (getAnime.await(manga.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    manga,
                                ) {
                                    try {
                                        val newChapters = updateManga(manga, fetchWindow)
                                            // SY -->
                                            .sortedByDescending { it.sourceOrder }.run {
                                                if (libraryPreferences.libraryMarkDuplicateEpisodes().get()) {
                                                    val readChapters = getEpisodesByAnimeId.await(manga.id).filter {
                                                        it.seen
                                                    }
                                                    val newReadChapters = this.filter { chapter ->
                                                        chapter.episodeNumber >= 0 &&
                                                            readChapters.any {
                                                                it.episodeNumber == chapter.episodeNumber
                                                            }
                                                    }

                                                    if (newReadChapters.isNotEmpty()) {
                                                        setSeenStatus.await(
                                                            true,
                                                            *newReadChapters.toTypedArray(),
                                                            // KMK -->
                                                            manually = false,
                                                            // KMK <--
                                                        )
                                                    }

                                                    this.filterNot { newReadChapters.contains(it) }
                                                } else {
                                                    this
                                                }
                                            }
                                        // SY <--

                                        if (newChapters.isNotEmpty()) {
                                            val chaptersToDownload = filterEpisodesForDownload.await(manga, newChapters)

                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadChapters(manga, chaptersToDownload)
                                                hasDownloads.set(true)
                                            }

                                            libraryPreferences.newUpdatesCount().getAndSet { it + newChapters.size }

                                            // Convert to the manga that contains new episodes
                                            newUpdates.add(manga to newChapters.toTypedArray())
                                        }
                                        clearErrorFromDB(mangaId = manga.id)
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoResultsException ->
                                                context.stringResource(MR.strings.no_chapters_error)
                                            // failedUpdates will already have the source,
                                            // don't need to copy it into the message
                                            is SourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )

                                            else -> e.message
                                        }
                                        writeErrorToDB(manga to errorMessage)
                                        failedUpdates.add(manga to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
            )
        }
    }

    private fun downloadChapters(anime: Anime, episodes: List<Episode>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        // SY -->
        if (anime.source == MERGED_SOURCE_ID) {
            val downloadingManga = runBlocking { getMergedAnimeForDownloading.await(anime.id) }
                .associateBy { it.id }
            episodes.groupBy { it.animeId }
                .forEach {
                    downloadManager.downloadEpisodes(
                        downloadingManga[it.key] ?: return@forEach,
                        it.value,
                        false,
                    )
                }

            return
        }
        // SY <--
        downloadManager.downloadEpisodes(anime, episodes, false)
    }

    /**
     * Updates the episodes for the given manga and adds them to the database.
     *
     * @param anime the manga to update.
     * @return a pair of the inserted and removed episodes.
     */
    private suspend fun updateManga(anime: Anime, fetchWindow: Pair<Long, Long>): List<Episode> {
        val source = sourceManager.getOrStub(anime.source)

        // Update manga metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkManga = source.getAnimeDetails(anime.toSAnime())
            updateAnime.awaitUpdateFromSource(anime, networkManga, manualFetch = false, coverCache)
        }

        if (source is MergedSource) {
            return source.fetchEpisodesAndSync(anime, false)
        }

        val chapters = source.getEpisodeList(anime.toSAnime())

        // Get manga from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbManga = getAnime.await(anime.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncEpisodesWithSource.await(chapters, dbManga, source, false, fetchWindow)
    }

    private suspend fun updateCovers() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()

        coroutineScope {
            mangaToUpdate.groupBy { it.anime.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.anime
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    manga,
                                ) {
                                    val source = sourceManager.get(manga.source) ?: return@withUpdateNotification
                                    try {
                                        val networkManga = source.getAnimeDetails(manga.toSAnime())
                                        val updatedManga = manga.prepUpdateCover(
                                            coverCache,
                                            networkManga,
                                            true,
                                        )
                                            .copyFrom(networkManga)
                                        try {
                                            updateAnime.await(updatedManga.toAnimeUpdate())
                                        } catch (e: Exception) {
                                            logcat(LogPriority.ERROR) { "Manga doesn't exist anymore" }
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun withUpdateNotification(
        updatingAnime: CopyOnWriteArrayList<Anime>,
        completed: AtomicInteger,
        anime: Anime,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAnime.add(anime)
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingAnime.remove(anime)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            mangaToUpdate.size,
        )
    }

    // KMK -->
    private suspend fun clearErrorFromDB(mangaId: Long) {
        deleteLibraryUpdateErrors.deleteAnimeError(animeId = mangaId)
    }

    private suspend fun writeErrorToDB(error: Pair<Anime, String?>) {
        val errorMessage = error.second ?: "???"
        val errorMessageId = insertLibraryUpdateErrorMessages.get(errorMessage)
            ?: insertLibraryUpdateErrorMessages.insert(
                libraryUpdateErrorMessage = LibraryUpdateErrorMessage(-1L, errorMessage),
            )

        insertLibraryUpdateErrors.upsert(
            LibraryUpdateError(id = -1L, animeId = error.first.id, messageId = errorMessageId),
        )
    }

    private suspend fun writeErrorsToDB(errors: List<Pair<Anime, String?>>) {
        val libraryErrors = errors.groupBy({ it.second }, { it.first })
        val errorMessages = insertLibraryUpdateErrorMessages.insertAll(
            libraryUpdateErrorMessages = libraryErrors.keys.map { errorMessage ->
                LibraryUpdateErrorMessage(-1L, errorMessage.orEmpty())
            },
        )
        val errorList = mutableListOf<LibraryUpdateError>()
        errorMessages.forEach {
            libraryErrors[it.second]?.forEach { manga ->
                errorList.add(LibraryUpdateError(id = -1L, animeId = manga.id, messageId = it.first))
            }
        }
        insertLibraryUpdateErrors.insertAll(errorList)
    }
    // KMK <--

    /**
     * Defines what should be updated within a service execution.
     */
    enum class Target {
        CHAPTERS, // Manga episodes
        COVERS, // Manga covers
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://mihon.app/docs/guides/troubleshooting/"

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        /**
         * Key that defines what should be updated.
         */
        private const val KEY_TARGET = "target"

        // SY -->
        /**
         * Key for group to update.
         */
        const val KEY_GROUP = "group"
        const val KEY_GROUP_EXTRA = "group_extra"
        // SY <--

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<LibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(
            context: Context,
            category: Category? = null,
            target: Target = Target.CHAPTERS,
            // SY -->
            group: Int = LibraryGroup.BY_DEFAULT,
            groupExtra: String? = null,
            // SY <--
        ): Boolean {
            val wm = context.workManager
            // Check if the LibraryUpdateJob is already running
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
                KEY_TARGET to target.name,
                // SY -->
                KEY_GROUP to group,
                KEY_GROUP_EXTRA to groupExtra,
                // SY <--
            )

            val syncPreferences: SyncPreferences = Injekt.get()

            // Always sync the data before library update if syncing is enabled.
            if (syncPreferences.isSyncEnabled()) {
                // Check if SyncDataJob is already running
                if (SyncDataJob.isRunning(context)) {
                    // SyncDataJob is already running
                    return false
                }

                // Define the SyncDataJob
                val syncDataJob = OneTimeWorkRequestBuilder<SyncDataJob>()
                    .addTag(SyncDataJob.TAG_MANUAL)
                    .build()

                // Chain SyncDataJob to run before LibraryUpdateJob
                val libraryUpdateJob = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                    .addTag(TAG)
                    .addTag(WORK_NAME_MANUAL)
                    .setInputData(inputData)
                    .build()

                wm.beginUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, syncDataJob)
                    .then(libraryUpdateJob)
                    .enqueue()
            } else {
                val request = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                    .addTag(TAG)
                    .addTag(WORK_NAME_MANUAL)
                    .setInputData(inputData)
                    .build()

                wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)
            }

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)
                    // KMK -->
                    val libraryUpdateStatus: LibraryUpdateStatus = Injekt.get()
                    runBlocking { libraryUpdateStatus.stop() }
                    // KMK <--

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
