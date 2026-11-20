package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.data.cover.MangaBakaCoverUpdater
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

@OptIn(ExperimentalAtomicApi::class)
class MetadataUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()

    // KMK -->
    private val mangaBakaCoverUpdater = MangaBakaCoverUpdater()
    private val libraryUpdateStatus: LibraryUpdateStatus = Injekt.get()
    // KMK <--

    private val notifier = LibraryUpdateNotifier(context)

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        setForegroundSafely()

        addMangaToQueue()

        return withIOContext {
            try {
                // KMK -->
                // Show the in-app progress banner, as a library update does.
                libraryUpdateStatus.start()
                val mangaBakaMode = inputData.getString(KEY_MANGABAKA_MODE)
                val showProgress: suspend (Manga, Int, Int) -> Unit = { manga, done, total ->
                    notifier.showProgressNotification(listOf(manga), done, total)
                }
                if (mangaBakaMode == MODE_RESTORE) {
                    mangaBakaCoverUpdater.restoreSourceCovers(mangaToUpdate.map { it.manga }, showProgress)
                } else {
                    // "Refresh library covers" also checks every MangaBaka cover, ignoring the usual daily wait.
                    // Done first: refreshing every source takes far longer, and the result shouldn't wait for it.
                    val coverFailures = mangaBakaCoverUpdater.updateAll(
                        mangaList = mangaToUpdate.map { it.manga },
                        force = true,
                        onProgress = showProgress,
                    )
                    notifier.showUpdateErrorNotification(coverFailures)
                    if (mangaBakaMode == null) updateMetadata()
                }
                // KMK <--
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
     */
    private suspend fun addMangaToQueue() {
        mangaToUpdate = getLibraryManga.await()
        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    val source = sourceManager.get(manga.source) ?: return@withUpdateNotification
                                    try {
                                        updateMangaFromRemote(
                                            source = source,
                                            manga = manga,
                                            fetchDetails = true,
                                            // KMK -->
                                            // Refresh same Url
                                            manualFetch = true,
                                            // KMK <--
                                        ).getOrThrow()
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
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInt,
        manga: Manga,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.fetchAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            mangaToUpdate.size,
        )
    }

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<MetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        // KMK -->
        /**
         * Re-checks every MangaBaka cover without refreshing the sources' covers (after the feature is
         * turned on or the cover language changes), or with [restore] puts the sources' covers back
         * (after it is turned off). Replaces a refresh already running, so the latest setting wins.
         */
        fun startMangaBakaCovers(context: Context, restore: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<MetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(workDataOf(KEY_MANGABAKA_MODE to if (restore) MODE_RESTORE else MODE_CHECK))
                .build()
            context.workManager.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.REPLACE, request)
        }

        private const val KEY_MANGABAKA_MODE = "mangabaka_mode"
        private const val MODE_CHECK = "check"
        private const val MODE_RESTORE = "restore"
        // KMK <--

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)
                }
        }
    }
}
