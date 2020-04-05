package eu.kanade.tachiyomi.data.backup

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.Backup.CATEGORIES
import eu.kanade.tachiyomi.data.backup.models.Backup.CHAPTERS
import eu.kanade.tachiyomi.data.backup.models.Backup.HISTORY
import eu.kanade.tachiyomi.data.backup.models.Backup.MANGA
import eu.kanade.tachiyomi.data.backup.models.Backup.MANGAS
import eu.kanade.tachiyomi.data.backup.models.Backup.TRACK
import eu.kanade.tachiyomi.data.backup.models.Backup.VERSION
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.notificationManager
import exh.BackupEntry
import exh.EXHMigrations
import exh.eh.EHentaiThrottleManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Restores backup from json file
 */
class BackupRestoreService : Service() {

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    /**
     * Subscription where the update is done.
     */
    private var job: Job? = null

    /**
     * The progress of a backup restore
     */
    private var restoreProgress = 0

    /**
     * Amount of manga in Json file (needed for restore)
     */
    private var restoreAmount = 0

    private var skippedAmount = 0

    private var totalAmount = 0

    /**
     * List containing errors
     */
    private val errors = mutableListOf<String>()

    /**
     * count of cancelled
     */
    private var cancelled = 0

    /**
     * List containing distinct errors
     */
    private val errorsMini = mutableListOf<String>()

    /**
     * List containing missing sources
     */
    private val sourcesMissing = mutableListOf<Long>()

    /**
     * Backup manager
     */
    private lateinit var backupManager: BackupManager

    /**
     * Database
     */
    private val db: DatabaseHelper by injectLazy()

    /**
     * Tracking manager
     */
    internal val trackManager: TrackManager by injectLazy()

    private val throttleManager = EHentaiThrottleManager()

    /**
     * Method called when the service is created. It injects dependencies and acquire the wake lock.
     */
    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_RESTORE_PROGRESS, progressNotification.build())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "BackupRestoreService:WakeLock")
        wakeLock.acquire(60 * 60 * 1000L /*1 hour*/)
    }

    /**
     * Method called when the service is destroyed. It destroys the running subscription and
     * releases the wake lock.
     */
    override fun onDestroy() {
        job?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val uri = intent.getParcelableExtra<Uri>(BackupConst.EXTRA_URI) ?: return START_NOT_STICKY

        throttleManager.resetThrottle()

        // Unsubscribe from any previous subscription if needed.
        job?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception)
            showErrorNotification(exception.message!!)
            stopSelf(startId)
        }
        job = GlobalScope.launch(handler) {
            restoreBackup(uri!!)
        }
        job?.invokeOnCompletion { stopSelf(startId) }

        return START_NOT_STICKY
    }

    /**
     * Restore a backup json file
     */
    private suspend fun restoreBackup(uri: Uri) {
        val reader = JsonReader(contentResolver.openInputStream(uri)!!.bufferedReader())
        val json = JsonParser.parseReader(reader).asJsonObject

        // Get parser version
        val version = json.get(VERSION)?.asInt ?: 1

        // Initialize manager
        backupManager = BackupManager(this, version)

        val mangasJson = json.get(MANGAS).asJsonArray

        val validManga = mangasJson.filter {
            val tmanga = backupManager.parser.fromJson<MangaImpl>(it.asJsonObject.get(MANGA))
            // EXH -->
            val migrated = EXHMigrations.migrateBackupEntry(
                    BackupEntry(
                            tmanga,
                            backupManager.parser.fromJson<List<ChapterImpl>>(JsonArray()),
                            backupManager.parser.fromJson<List<String>>(JsonArray()),
                            backupManager.parser.fromJson<List<DHistory>>(JsonArray()),
                            backupManager.parser.fromJson<List<TrackImpl>>(JsonArray())
                    )
            )
            val (manga, _, _, _, _) = migrated
            val sourced = backupManager.sourceManager.get(manga.source) != null
            if (!sourced) {
                restoreAmount -= 1
            }
            sourced
        }

        totalAmount = mangasJson.size()
        skippedAmount = mangasJson.size() - validManga.count()
        restoreAmount = validManga.count() + 1

        errors.clear()
        errorsMini.clear()
        cancelled = 0
        // Restore categories
        restoreCategories(json, backupManager)

        mangasJson.forEach {
            restoreManga(it.asJsonObject, backupManager)
        }

        notificationManager.cancel(Notifications.ID_RESTORE_PROGRESS)

        cancelled = errors.count { it -> it.contains("standalonecoroutine", true) }
        var tmpErrors = errors.filter { it -> !it.contains("standalonecoroutine", true) }
        errors.clear()
        errors.addAll(tmpErrors)

        val logFile = writeErrorLog()
        showResultNotification(logFile.parent, logFile.name)
    }

    /**Restore categories if they were backed up
     *
     */
    private fun restoreCategories(json: JsonObject, backupManager: BackupManager) {
        val element = json.get(CATEGORIES)
        if (element != null) {
            backupManager.restoreCategories(element.asJsonArray)
            restoreProgress += 1
            showProgressNotification(restoreProgress, restoreAmount, "Categories added")
        } else {
            restoreAmount -= 1
        }
    }

    /**
     * Restore manga from json  this should be refactored more at some point to prevent the manga object from being mutable
     */
    private suspend fun restoreManga(obj: JsonObject, backupManager: BackupManager) {
        val tmanga = backupManager.parser.fromJson<MangaImpl>(obj.get(MANGA))
        val tchapters = backupManager.parser.fromJson<List<ChapterImpl>>(obj.get(CHAPTERS)
                ?: JsonArray())
        val tcategories = backupManager.parser.fromJson<List<String>>(obj.get(CATEGORIES)
                ?: JsonArray())
        val thistory = backupManager.parser.fromJson<List<DHistory>>(obj.get(HISTORY)
                ?: JsonArray())
        val ttracks = backupManager.parser.fromJson<List<TrackImpl>>(obj.get(TRACK) ?: JsonArray())
        // EXH -->
        val migrated = EXHMigrations.migrateBackupEntry(
                BackupEntry(
                        tmanga,
                        tchapters,
                        tcategories,
                        thistory,
                        ttracks
                )
        )
        val (manga, chapters, categories, history, tracks) = migrated
        val source = backupManager.sourceManager.getOrStub(manga.source)
        // <-- EXH
        try {
            val dbManga = backupManager.getMangaFromDatabase(manga)
            val dbMangaExists = dbManga != null
            if (dbMangaExists) {
                // Manga in database copy information from manga already in database
                backupManager.restoreMangaNoFetch(manga, dbManga!!)
            } else {
                // manga gets details from network
                backupManager.restoreMangaFetch(source, manga)
            }
            if (!dbMangaExists || !backupManager.restoreChaptersForManga(manga, chapters)) {
                // manga gets chapters added
                backupManager.restoreChapterFetch(source, manga, chapters, throttleManager)
            }
            // Restore categories
            backupManager.restoreCategoriesForManga(manga, categories)
            // Restore history
            backupManager.restoreHistoryForManga(history)
            // Restore tracking
            backupManager.restoreTrackForManga(manga, tracks)
            trackingFetch(manga, tracks)
            restoreProgress += 1
            showProgressNotification(restoreProgress, restoreAmount, manga.title)
        } catch (e: Exception) {
            Timber.e(e)
            errors.add("${manga.title} - ${e.message}")
        }
    }

    /**
     * [refreshes tracking information
     * @param manga manga that needs updating.
     * @param tracks list containing tracks from restore file.
     */
    private fun trackingFetch(manga: Manga, tracks: List<Track>) {
        tracks.forEach { track ->
            val service = trackManager.getService(track.sync_id)
            if (service != null && service.isLogged) {
                service.refresh(track)
                        .doOnNext { db.insertTrack(it).executeAsBlocking() }
                        .onErrorReturn {
                            errors.add("${manga.title} - ${it.message}")
                            track
                        }
            } else {
                errors.add("${manga.title} - ${service?.name} not logged in")
            }
        }
    }

    /**
     * Write errors to error log
     */
    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val destFile = File(externalCacheDir, "tachiyomi_restore.log")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                destFile.bufferedWriter().use { out ->
                    errors.forEach { message ->
                        out.write("$message\n")
                    }
                }
                return destFile
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return File("")
    }

    /**
     * keep a partially constructed progress notification for resuse
     */
    private val progressNotification by lazy {
        NotificationCompat.Builder(this, Notifications.CHANNEL_RESTORE)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_update_black_128dp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .addAction(R.drawable.ic_clear_grey, getString(android.R.string.cancel), cancelIntent)
    }

    /**
     * Pending intent of action that cancels the library update
     */
    private val cancelIntent by lazy {
        NotificationReceiver.cancelRestorePendingBroadcast(this)
    }

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * @param manga the manga that's being updated.
     * @param current the current progress.
     * @param total the total progress.
     */
    private fun showProgressNotification(current: Int, total: Int, title: String) {
        notificationManager.notify(Notifications.ID_RESTORE_PROGRESS, progressNotification
                .setContentTitle(title)
                .setProgress(total, current, false)
                .build())
    }

    /**
     * Show the result notification with option to show the error log
     */
    private fun showResultNotification(path: String?, file: String?) {
        val content = listOf(getString(R.string.restore_completed_content, restoreProgress.toString()),
                getString(R.string.restore_completed_content_2, errors.size.toString()),
                getString(R.string.restore_completed_content_3, skippedAmount.toString(), totalAmount.toString()),
                getString(R.string.restore_completed_content_4, cancelled.toString(), totalAmount.toString())).joinToString("\n")

        val resultNotification = NotificationCompat.Builder(this, Notifications.CHANNEL_RESTORE)
                .setContentTitle(getString(R.string.restore_completed))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setSmallIcon(R.drawable.ic_update_black_128dp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
        if (errors.size > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
            resultNotification.addAction(R.drawable.ic_clear_grey, getString(R.string.notification_action_error_log), getErrorLogIntent(path, file))
        }
        notificationManager.notify(Notifications.ID_RESTORE_COMPLETE, resultNotification.build())
    }

    /**Show an error notification if something happens that prevents the restore from starting/working
     *
     */
    private fun showErrorNotification(errorMessage: String) {
        val resultNotification = NotificationCompat.Builder(this, Notifications.CHANNEL_RESTORE)
                .setContentTitle(getString(R.string.restore_error))
                .setContentText(errorMessage)
                .setSmallIcon(R.drawable.ic_error_grey)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(this, R.color.md_red_500))
        notificationManager.notify(Notifications.ID_RESTORE_ERROR, resultNotification.build())
    }

    /**Get the PendingIntent for the error log
     *
     */
    private fun getErrorLogIntent(path: String, file: String): PendingIntent {
        val destFile = File(path, file!!)
        val uri = destFile.getUriCompat(applicationContext)
        return NotificationReceiver.openFileExplorerPendingActivity(this@BackupRestoreService, uri)
    }

    companion object {

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean =
                context.isServiceRunning(BackupRestoreService::class.java)

        /**
         * Starts a service to restore a backup from Json
         *
         * @param context context of application
         * @param uri path of Uri
         */
        fun start(context: Context, uri: Uri) {
            if (!isRunning(context)) {
                val intent = Intent(context, BackupRestoreService::class.java).apply {
                    putExtra(BackupConst.EXTRA_URI, uri)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
            }
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, BackupRestoreService::class.java))
        }
    }
}
