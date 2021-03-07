package exh.eh

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION
import exh.log.xLog
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadataAsync
import exh.source.isEhBasedManga
import exh.util.cancellable
import exh.util.executeOnIO
import exh.util.jobScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.ArrayList
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.hours

class EHentaiUpdateWorker : JobService() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val db: DatabaseHelper by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val updateHelper: EHentaiUpdateHelper by injectLazy()
    private val logger: Logger = xLog()

    private val updateNotifier by lazy { LibraryUpdateNotifier(this) }

    /**
     * This method is called if the system has determined that you must stop execution of your job
     * even before you've had a chance to call [.jobFinished].
     *
     *
     * This will happen if the requirements specified at schedule time are no longer met. For
     * example you may have requested WiFi with
     * [android.app.job.JobInfo.Builder.setRequiredNetworkType], yet while your
     * job was executing the user toggled WiFi. Another example is if you had specified
     * [android.app.job.JobInfo.Builder.setRequiresDeviceIdle], and the phone left its
     * idle maintenance window. You are solely responsible for the behavior of your application
     * upon receipt of this message; your app will likely start to misbehave if you ignore it.
     *
     *
     * Once this method returns, the system releases the wakelock that it is holding on
     * behalf of the job.
     *
     * @param params The parameters identifying this job, as supplied to
     * the job in the [.onStartJob] callback.
     * @return `true` to indicate to the JobManager whether you'd like to reschedule
     * this job based on the retry criteria provided at job creation-time; or `false`
     * to end the job entirely.  Regardless of the value returned, your job must stop executing.
     */
    override fun onStopJob(params: JobParameters?): Boolean {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        return false
    }

    /**
     * Called to indicate that the job has begun executing.  Override this method with the
     * logic for your job.  Like all other component lifecycle callbacks, this method executes
     * on your application's main thread.
     *
     *
     * Return `true` from this method if your job needs to continue running.  If you
     * do this, the job remains active until you call
     * [.jobFinished] to tell the system that it has completed
     * its work, or until the job's required constraints are no longer satisfied.  For
     * example, if the job was scheduled using
     * [setRequiresCharging(true)][JobInfo.Builder.setRequiresCharging],
     * it will be immediately halted by the system if the user unplugs the device from power,
     * the job's [.onStopJob] callback will be invoked, and the app
     * will be expected to shut down all ongoing work connected with that job.
     *
     *
     * The system holds a wakelock on behalf of your app as long as your job is executing.
     * This wakelock is acquired before this method is invoked, and is not released until either
     * you call [.jobFinished], or after the system invokes
     * [.onStopJob] to notify your job that it is being shut down
     * prematurely.
     *
     *
     * Returning `false` from this method means your job is already finished.  The
     * system's wakelock for the job will be released, and [.onStopJob]
     * will not be invoked.
     *
     * @param params Parameters specifying info about this job, including the optional
     * extras configured with [     This object serves to identify this specific running job instance when calling][JobInfo.Builder.setExtras]
     */
    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            startUpdating()
            logger.d("Update job completed!")
            jobFinished(params, false)
        }

        return true
    }

    private suspend fun startUpdating() {
        logger.d("Update job started!")
        val startTime = System.currentTimeMillis()

        logger.d("Finding manga with metadata...")
        val metadataManga = db.getFavoriteMangaWithMetadata().executeOnIO()

        logger.d("Filtering manga and raising metadata...")
        val curTime = System.currentTimeMillis()
        val allMeta = metadataManga.asFlow().cancellable().mapNotNull { manga ->
            if (!manga.isEhBasedManga()) {
                return@mapNotNull null
            }

            val meta = db.getFlatMetadataForManga(manga.id!!).executeOnIO()
                ?: return@mapNotNull null

            val raisedMeta = meta.raise<EHentaiSearchMetadata>()

            // Don't update galleries too frequently
            if (raisedMeta.aged || (curTime - raisedMeta.lastUpdateCheck < MIN_BACKGROUND_UPDATE_FREQ && DebugToggles.RESTRICT_EXH_GALLERY_UPDATE_CHECK_FREQUENCY.enabled)) {
                return@mapNotNull null
            }

            val chapter = db.getChapters(manga.id!!).executeOnIO().minByOrNull {
                it.date_upload
            }

            UpdateEntry(manga, raisedMeta, chapter)
        }.toList().sortedBy { it.meta.lastUpdateCheck }

        logger.d("Found %s manga to update, starting updates!", allMeta.size)
        val mangaMetaToUpdateThisIter = allMeta.take(UPDATES_PER_ITERATION)

        var failuresThisIteration = 0
        var updatedThisIteration = 0
        val updatedManga = ArrayList<Pair<Manga, Array<Chapter>>>()
        val modifiedThisIteration = mutableSetOf<Long>()

        try {
            for ((index, entry) in mangaMetaToUpdateThisIter.withIndex()) {
                val (manga, meta) = entry
                if (failuresThisIteration > MAX_UPDATE_FAILURES) {
                    logger.w("Too many update failures, aborting...")
                    break
                }

                logger.d(
                    "Updating gallery (index: %s, manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s, modifiedThisIteration.size: %s)...",
                    index,
                    manga.id,
                    meta.gId,
                    meta.gToken,
                    failuresThisIteration,
                    modifiedThisIteration.size
                )

                if (manga.id in modifiedThisIteration) {
                    // We already processed this manga!
                    logger.w("Gallery already updated this iteration, skipping...")
                    updatedThisIteration++
                    continue
                }

                val (new, chapters) = try {
                    updateEntryAndGetChapters(manga)
                } catch (e: GalleryNotUpdatedException) {
                    if (e.network) {
                        failuresThisIteration++

                        logger.e("> Network error while updating gallery!", e)
                        logger.e(
                            "> (manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s)",
                            manga.id,
                            meta.gId,
                            meta.gToken,
                            failuresThisIteration
                        )
                    }

                    continue
                }

                if (chapters.isEmpty()) {
                    logger.e(
                        "No chapters found for gallery (manga.id: %s, meta.gId: %s, meta.gToken: %s, failures-so-far: %s)!",
                        manga.id,
                        meta.gId,
                        meta.gToken,
                        failuresThisIteration
                    )

                    continue
                }

                // Find accepted root and discard others
                val (acceptedRoot, discardedRoots, hasNew) =
                    updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters).single()

                if ((new.isNotEmpty() && manga.id == acceptedRoot.manga.id) ||
                    (hasNew && updatedManga.none { it.first.id == acceptedRoot.manga.id })
                ) {
                    updatedManga += Pair(acceptedRoot.manga, new.toTypedArray())
                }

                modifiedThisIteration += acceptedRoot.manga.id!!
                modifiedThisIteration += discardedRoots.map { it.manga.id!! }
                updatedThisIteration++
            }
        } finally {
            prefs.exhAutoUpdateStats().set(
                Json.encodeToString(
                    EHentaiUpdaterStats(
                        startTime,
                        allMeta.size,
                        updatedThisIteration
                    )
                )
            )

            if (updatedManga.isNotEmpty()) {
                updateNotifier.showUpdateNotifications(updatedManga)
            }
        }
    }

    // New, current
    private suspend fun updateEntryAndGetChapters(manga: Manga): Pair<List<Chapter>, List<Chapter>> {
        val source = sourceManager.get(manga.source) as? EHentai
            ?: throw GalleryNotUpdatedException(false, IllegalStateException("Missing EH-based source (${manga.source})!"))

        try {
            val updatedManga = source.getMangaDetails(manga.toMangaInfo())
            manga.copyFrom(updatedManga.toSManga())
            db.insertManga(manga).executeOnIO()

            val newChapters = source.getChapterList(manga.toMangaInfo())
                .map { it.toSChapter() }

            val (new, _) = syncChaptersWithSource(db, newChapters, manga, source) // Not suspending, but does block, maybe fix this?
            return new to db.getChapters(manga).executeOnIO()
        } catch (t: Throwable) {
            if (t is EHentai.GalleryNotFoundException) {
                val meta = db.getFlatMetadataForManga(manga.id!!).executeOnIO()?.raise<EHentaiSearchMetadata>()
                if (meta != null) {
                    // Age dead galleries
                    logger.d("Aged %s - notfound", manga.id)
                    meta.aged = true
                    db.insertFlatMetadataAsync(meta.flatten()).await()
                }
                throw GalleryNotUpdatedException(false, t)
            }
            throw GalleryNotUpdatedException(true, t)
        }
    }

    companion object {
        private const val MAX_UPDATE_FAILURES = 5

        @OptIn(ExperimentalTime::class)
        private val MIN_BACKGROUND_UPDATE_FREQ = 1.days.toLongMilliseconds()

        private const val JOB_ID_UPDATE_BACKGROUND = 700000
        private const val JOB_ID_UPDATE_BACKGROUND_TEST = 700001

        private val logger by lazy { XLog.tag("EHUpdaterScheduler") }

        private fun Context.componentName(): ComponentName =
            ComponentName(this, EHentaiUpdateWorker::class.java)

        private fun Context.baseBackgroundJobInfo(isTest: Boolean): JobInfo.Builder =
            JobInfo.Builder(
                if (isTest) JOB_ID_UPDATE_BACKGROUND_TEST
                else JOB_ID_UPDATE_BACKGROUND,
                componentName()
            )

        private fun Context.periodicBackgroundJobInfo(
            period: Long,
            requireCharging: Boolean,
            requireUnmetered: Boolean
        ): JobInfo = baseBackgroundJobInfo(false)
            .setPeriodic(period)
            .setPersisted(true)
            .setRequiredNetworkType(
                if (requireUnmetered) JobInfo.NETWORK_TYPE_UNMETERED
                else JobInfo.NETWORK_TYPE_ANY
            )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRequiresBatteryNotLow(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setEstimatedNetworkBytes(
                        15000L * UPDATES_PER_ITERATION,
                        1000L * UPDATES_PER_ITERATION
                    )
                }
            }
            .setRequiresCharging(requireCharging)
//            .setRequiresDeviceIdle(true) Job never seems to run with this
            .build()

        private fun Context.testBackgroundJobInfo(): JobInfo = baseBackgroundJobInfo(true)
            .setOverrideDeadline(1)
            .build()

        fun launchBackgroundTest(context: Context): String =
            if (context.jobScheduler.schedule(context.testBackgroundJobInfo()) == JobScheduler.RESULT_FAILURE) {
                logger.e("Failed to schedule background test job!")
                "Failed"
            } else {
                logger.d("Successfully scheduled background test job!")
                "Success"
            }

        fun scheduleBackground(context: Context, prefInterval: Int? = null) {
            cancelBackground(context)

            val preferences = Injekt.get<PreferencesHelper>()
            val duration = prefInterval ?: preferences.exhAutoUpdateFrequency().get()
            if (duration > 0) {
                val restrictions = preferences.exhAutoUpdateRequirements().get()
                val acRestriction = "ac" in restrictions
                val wifiRestriction = "wifi" in restrictions

                @OptIn(ExperimentalTime::class)
                val jobInfo = context.periodicBackgroundJobInfo(
                    duration.hours.toLongMilliseconds(),
                    acRestriction,
                    wifiRestriction
                )

                if (context.jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_FAILURE) {
                    logger.e("Failed to schedule background update job!")
                } else {
                    logger.d("Successfully scheduled background update job!")
                }
            }
        }

        fun cancelBackground(context: Context) {
            context.jobScheduler.cancel(JOB_ID_UPDATE_BACKGROUND)
        }
    }
}

data class UpdateEntry(val manga: Manga, val meta: EHentaiSearchMetadata, val rootChapter: Chapter?)

object EHentaiUpdateWorkerConstants {
    const val UPDATES_PER_ITERATION = 50

    @OptIn(ExperimentalTime::class)
    val GALLERY_AGE_TIME = 365.days.toLongMilliseconds()
}
