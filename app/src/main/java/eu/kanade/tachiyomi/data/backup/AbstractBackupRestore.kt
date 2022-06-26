package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.data.DatabaseHandler
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.Job
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class AbstractBackupRestore<T : AbstractBackupManager>(protected val context: Context, protected val notifier: BackupNotifier) {

    protected val handler: DatabaseHandler by injectLazy()
    protected val trackManager: TrackManager by injectLazy()

    // SY -->
    protected val customMangaManager: CustomMangaManager by injectLazy()
    // SY <--

    var job: Job? = null

    protected lateinit var backupManager: T

    protected var restoreAmount = 0
    protected var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    protected var sourceMapping: Map<Long, String> = emptyMap()

    protected val errors = mutableListOf<Pair<Date, String>>()

    abstract suspend fun performRestore(uri: Uri): Boolean

    suspend fun restoreBackup(uri: Uri): Boolean {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        if (!performRestore(uri)) {
            return false
        }

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
        return true
    }

    /**
     * Refreshes tracking information.
     *
     * @param manga manga that needs updating.
     * @param tracks list containing tracks from restore file.
     */
    internal suspend fun updateTracking(manga: Manga, tracks: List<Track>) {
        tracks.forEach { track ->
            val service = trackManager.getService(track.sync_id.toLong())
            if (service != null && service.isLogged) {
                try {
                    val updatedTrack = service.refresh(track)
                    handler.await {
                        manga_syncQueries.insert(
                            updatedTrack.manga_id,
                            updatedTrack.sync_id.toLong(),
                            updatedTrack.media_id,
                            updatedTrack.library_id,
                            updatedTrack.title,
                            updatedTrack.last_chapter_read.toDouble(),
                            updatedTrack.total_chapters.toLong(),
                            updatedTrack.status.toLong(),
                            updatedTrack.score,
                            updatedTrack.tracking_url,
                            updatedTrack.started_reading_date,
                            updatedTrack.finished_reading_date,
                        )
                    }
                } catch (e: Exception) {
                    errors.add(Date() to "${manga.title} - ${e.message}")
                }
            } else {
                val serviceName = service?.nameRes()?.let { context.getString(it) }
                errors.add(Date() to "${manga.title} - ${context.getString(R.string.tracker_not_logged_in, serviceName)}")
            }
        }
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of manga
     * @param title title of restored manga
     */
    internal fun showRestoreProgress(
        progress: Int,
        amount: Int,
        title: String,
    ) {
        notifier.showRestoreProgress(title, progress, amount)
    }

    internal fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
