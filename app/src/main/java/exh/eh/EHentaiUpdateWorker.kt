package exh.eh

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.preference.CHARGING
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.UNMETERED_NETWORK
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
import exh.util.days
import exh.util.executeOnIO
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class EHentaiUpdateWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    private val db: DatabaseHelper by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val updateHelper: EHentaiUpdateHelper by injectLazy()
    private val logger: Logger = xLog()

    private val updateNotifier by lazy { LibraryUpdateNotifier(context) }

    override suspend fun doWork(): Result {
        return try {
            startUpdating()
            logger.d("Update job completed!")
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
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
        val updatedManga = mutableListOf<Pair<Manga, Array<Chapter>>>()
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
                    updatedManga += acceptedRoot.manga to new.toTypedArray()
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

        private val MIN_BACKGROUND_UPDATE_FREQ = 1.days.inWholeMilliseconds

        private const val TAG = "EHBackgroundUpdater"

        private val logger by lazy { XLog.tag("EHUpdaterScheduler") }

        fun launchBackgroundTest(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<EHentaiUpdateWorker>().build())
        }

        fun scheduleBackground(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val interval = prefInterval ?: preferences.exhAutoUpdateFrequency().get()
            if (interval > 0) {
                val restrictions = preferences.exhAutoUpdateRequirements().get()
                val acRestriction = CHARGING in restrictions
                val wifiRestriction = if (UNMETERED_NETWORK in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(wifiRestriction)
                    .setRequiresCharging(acRestriction)
                    .build()

                val request = PeriodicWorkRequestBuilder<EHentaiUpdateWorker>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES
                )
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
                logger.d("Successfully scheduled background update job!")
            } else {
                cancelBackground(context)
            }
        }

        fun cancelBackground(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}

data class UpdateEntry(val manga: Manga, val meta: EHentaiSearchMetadata, val rootChapter: Chapter?)

object EHentaiUpdateWorkerConstants {
    const val UPDATES_PER_ITERATION = 50

    val GALLERY_AGE_TIME = 365.days.inWholeMilliseconds
}
