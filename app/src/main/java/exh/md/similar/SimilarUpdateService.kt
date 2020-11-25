package exh.md.similar

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import com.elvishew.xlog.XLog
import com.squareup.moshi.JsonReader
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.notificationManager
import exh.md.similar.sql.models.MangaSimilarImpl
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import retrofit2.awaitResponse
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit

class SimilarUpdateService(
    val db: DatabaseHelper = Injekt.get()
) : Service() {

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    var similarServiceScope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Subscription where the update is done.
     */
    private var job: Job? = null

    /**
     * Pending intent of action that cancels the library update
     */
    private val cancelIntent by lazy {
        NotificationReceiver.cancelSimilarUpdatePendingBroadcast(this)
    }

    private val progressNotification by lazy {
        NotificationCompat.Builder(this, Notifications.CHANNEL_SIMILAR)
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher))
            .setSmallIcon(R.drawable.ic_tachi)
            .setOngoing(true)
            .setContentTitle(getString(R.string.similar_loading_progress_start))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_close_24dp,
                getString(android.R.string.cancel),
                cancelIntent
            )
    }

    /**
     * Method called when the service is created. It injects dagger dependencies and acquire
     * the wake lock.
     */
    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_SIMILAR_PROGRESS, progressNotification.build())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimilarUpdateService:WakeLock"
        )
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(30))
    }

    /**
     * Method called when the service is destroyed. It destroys subscriptions and releases the wake
     * lock.
     */
    override fun onDestroy() {
        job?.cancel()
        similarServiceScope.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent) = null

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return Service.START_NOT_STICKY

        // Unsubscribe from any previous subscription if needed.
        job?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            XLog.tag("SimilarUpdateService").enableStackTrace(2).e(exception)
            stopSelf(startId)
            showResultNotification(true)
            cancelProgressNotification()
        }
        job = similarServiceScope.launch(handler) {
            updateSimilar()
        }
        job?.invokeOnCompletion { stopSelf(startId) }

        return START_REDELIVER_INTENT
    }

    /**
     * Method that updates the similar database for manga
     */
    private suspend fun updateSimilar() = withContext(Dispatchers.IO) {
        val response = SimilarHttpService.create().getSimilarResults().awaitResponse()
        if (!response.isSuccessful) {
            throw Exception("Error trying to download similar file")
        }
        val destinationFile = File(filesDir, "neko-similar.json")
        val buffer = withContext(Dispatchers.IO) { destinationFile.sink().buffer() }

        // write json to file
        response.body()?.byteStream()?.source()?.use { input ->
            buffer.use { output ->
                output.writeAll(input)
            }
        }

        val listSimilar = getSimilar(destinationFile)

        // Delete the old similar table
        db.deleteAllSimilar().executeAsBlocking()

        val totalManga = listSimilar.size

        // Loop through each and insert into the database

        val dataToInsert = listSimilar.mapIndexed { index, similarFromJson ->
            showProgressNotification(index, totalManga)

            if (similarFromJson.similarIds.size != similarFromJson.similarTitles.size) {
                return@mapIndexed null
            }

            val similar = MangaSimilarImpl()
            similar.id = index.toLong()
            similar.manga_id = similarFromJson.id.toLong()
            similar.matched_ids = similarFromJson.similarIds.joinToString(MangaSimilarImpl.DELIMITER)
            similar.matched_titles = similarFromJson.similarTitles.joinToString(MangaSimilarImpl.DELIMITER)
            return@mapIndexed similar
        }.filterNotNull()

        showProgressNotification(dataToInsert.size, totalManga)

        if (dataToInsert.isNotEmpty()) {
            db.insertSimilar(dataToInsert).executeAsBlocking()
        }
        destinationFile.delete()
        showResultNotification(!this.isActive)
        cancelProgressNotification()
    }

    private fun getSimilar(destinationFile: File): List<SimilarFromJson> {
        val reader = JsonReader.of(destinationFile.source().buffer())

        var processingManga = false
        var processingTitles = false
        var mangaId: String? = null
        var similarIds = mutableListOf<String>()
        var similarTitles = mutableListOf<String>()
        val similars = mutableListOf<SimilarFromJson>()

        while (reader.peek() != JsonReader.Token.END_DOCUMENT) {
            val nextToken = reader.peek()

            if (JsonReader.Token.BEGIN_OBJECT == nextToken) {
                reader.beginObject()
            } else if (JsonReader.Token.NAME == nextToken) {
                val name = reader.nextName()
                if (!processingManga && name.isDigitsOnly()) {
                    processingManga = true
                    // similar add id
                    mangaId = name
                } else if (name == "m_titles") {
                    processingTitles = true
                }
            } else if (JsonReader.Token.BEGIN_ARRAY == nextToken) {
                reader.beginArray()
            } else if (JsonReader.Token.END_ARRAY == nextToken) {
                reader.endArray()
                if (processingTitles) {
                    processingManga = false
                    processingTitles = false
                    similars.add(SimilarFromJson(mangaId!!, similarIds.toList(), similarTitles.toList()))
                    mangaId = null
                    similarIds = mutableListOf()
                    similarTitles = mutableListOf()
                }
            } else if (JsonReader.Token.NUMBER == nextToken) {
                similarIds.add(reader.nextInt().toString())
            } else if (JsonReader.Token.STRING == nextToken) {
                if (processingTitles) {
                    similarTitles.add(reader.nextString())
                }
            } else if (JsonReader.Token.END_OBJECT == nextToken) {
                reader.endObject()
            }
        }

        return similars
    }

    data class SimilarFromJson(val id: String, val similarIds: List<String>, val similarTitles: List<String>)

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * @param current the current progress.
     * @param total the total progress.
     */
    private fun showProgressNotification(current: Int, total: Int) {
        notificationManager.notify(
            Notifications.ID_SIMILAR_PROGRESS,
            progressNotification
                .setContentTitle(
                    getString(
                        R.string.similar_loading_percent,
                        current,
                        total
                    )
                )
                .setProgress(total, current, false)
                .build()
        )
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * @param error if the result was a error.
     */
    private fun showResultNotification(error: Boolean = false) {
        val title = if (error) {
            getString(R.string.similar_loading_complete_error)
        } else {
            getString(
                R.string.similar_loading_complete
            )
        }

        val result = NotificationCompat.Builder(this, Notifications.CHANNEL_SIMILAR)
            .setContentTitle(title)
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher))
            .setSmallIcon(R.drawable.ic_tachi)
            .setAutoCancel(true)
        NotificationManagerCompat.from(this)
            .notify(Notifications.ID_SIMILAR_COMPLETE, result.build())
    }

    /**
     * Cancels the progress notification.
     */
    private fun cancelProgressNotification() {
        notificationManager.cancel(Notifications.ID_SIMILAR_PROGRESS)
    }

    companion object {

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(SimilarUpdateService::class.java)
        }

        /**
         * Starts the service. It will be started only if there isn't another instance already
         * running.
         *
         * @param context the application context.
         */
        fun start(context: Context) {
            if (!isRunning(context)) {
                val intent = Intent(context, SimilarUpdateService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, SimilarUpdateService::class.java))
        }
    }
}
