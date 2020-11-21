package eu.kanade.tachiyomi.data.backup

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.JsonArray
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID
import eu.kanade.tachiyomi.data.database.models.Manga
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.full.FullBackupManager
import eu.kanade.tachiyomi.data.backup.legacy.LegacyBackupManager
import eu.kanade.tachiyomi.data.backup.models.AbstractBackupManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.isServiceRunning

/**
 * [IntentService] used to backup [Manga] information to [JsonArray]
 */
class BackupCreateService : IntentService(NAME) {

    companion object {
        // Name of class
        private const val NAME = "BackupCreateService"

        // Options for backup
        private const val EXTRA_FLAGS = "$ID.$NAME.EXTRA_FLAGS"

        // Filter options
        internal const val BACKUP_CATEGORY = 0x1
        internal const val BACKUP_CATEGORY_MASK = 0x1
        internal const val BACKUP_CHAPTER = 0x2
        internal const val BACKUP_CHAPTER_MASK = 0x2
        internal const val BACKUP_HISTORY = 0x4
        internal const val BACKUP_HISTORY_MASK = 0x4
        internal const val BACKUP_TRACK = 0x8
        internal const val BACKUP_TRACK_MASK = 0x8
        internal const val BACKUP_ALL = 0xF

        /**
         * Make a backup from library
         *
         * @param context context of application
         * @param uri path of Uri
         * @param flags determines what to backup
         */
        fun start(context: Context, uri: Uri, flags: Int, type: Int) {
            if (!isRunning(context)) {
                val intent = Intent(context, BackupCreateService::class.java).apply {
                    putExtra(BackupConst.EXTRA_URI, uri)
                    putExtra(BackupConst.EXTRA_FLAGS, flags)
                    putExtra(BackupConst.EXTRA_TYPE, type)
                }
                ContextCompat.startForegroundService(context, intent)
            }
            context.startService(intent)
        }
    }

    private val backupManager by lazy { BackupManager(this) }

    private lateinit var backupManager: AbstractBackupManager
    private lateinit var notifier: BackupNotifier

    override fun onCreate() {
        super.onCreate()

        notifier = BackupNotifier(this)
        wakeLock = acquireWakeLock(javaClass.name)

        startForeground(Notifications.ID_BACKUP_PROGRESS, notifier.showBackupProgress().build())
    }

    override fun stopService(name: Intent?): Boolean {
        destroyJob()
        return super.stopService(name)
    }

    override fun onDestroy() {
        destroyJob()
        super.onDestroy()
    }

    private fun destroyJob() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        try {
            val uri = intent.getParcelableExtra<Uri>(BackupConst.EXTRA_URI)
            val backupFlags = intent.getIntExtra(BackupConst.EXTRA_FLAGS, 0)
            val backupType = intent.getIntExtra(BackupConst.EXTRA_TYPE, BackupConst.BACKUP_TYPE_LEGACY)
            backupManager = if (backupType == BackupConst.BACKUP_TYPE_FULL) FullBackupManager(this) else LegacyBackupManager(this)

            val backupFileUri = backupManager.createBackup(uri, backupFlags, false)?.toUri()
            val unifile = UniFile.fromUri(this, backupFileUri)
            notifier.showBackupComplete(unifile)
        } catch (e: Exception) {
            notifier.showBackupError(e.message)
        }
    }
}
