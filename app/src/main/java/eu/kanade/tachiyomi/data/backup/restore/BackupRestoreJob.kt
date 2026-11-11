package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.BackupRestoreStatus
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    // KMK -->
    private val backupRestoreStatus: BackupRestoreStatus = Injekt.get()
    private val syncPreferences: SyncPreferences = Injekt.get()
    // KMK <--

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        // KMK -->
        backupRestoreStatus.start()
        // KMK <--

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        setForegroundSafely()

        return try {
            val restored = BackupRestorer(context, notifier, isSync).restore(uri, options)
            // KMK --> Only advance the v2 cursor after a successful restore.
            if (restored && inputData.getBoolean(V2_SYNC_KEY, false)) {
                syncPreferences.syncCursor().set(inputData.getLong(V2_CURSOR_KEY, 0L))
                syncPreferences.fullSyncRequested().set(inputData.getBoolean(V2_FULL_REQUESTED_KEY, false))
                syncPreferences.lastPushedAt().set(inputData.getLong(V2_LAST_PUSHED_KEY, 0L))
                val lastFullSync = inputData.getLong(V2_LAST_FULL_KEY, 0L)
                if (lastFullSync > 0L) syncPreferences.lastFullSync().set(lastFullSync)
            }
            // KMK <--
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
            // KMK -->
            backupRestoreStatus.stop()
            // KMK <--
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
            v2State: eu.kanade.tachiyomi.data.sync.service.SyncYomiV2State? = null,
        ) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                SYNC_KEY to sync,
                OPTIONS_KEY to options.asBooleanArray(),
                V2_SYNC_KEY to (v2State != null),
                V2_CURSOR_KEY to (v2State?.cursor ?: 0L),
                V2_FULL_REQUESTED_KEY to (v2State?.fullSyncRequested ?: false),
                V2_LAST_PUSHED_KEY to (v2State?.lastPushedAt ?: 0L),
                V2_LAST_FULL_KEY to (v2State?.lastFullSync ?: 0L),
            )
            val request = OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
            // KMK -->
            val backupRestoreStatus: BackupRestoreStatus = Injekt.get()
            runBlocking { backupRestoreStatus.stop() }
            // KMK <--
        }
    }
}

private const val TAG = "BackupRestore"

private const val LOCATION_URI_KEY = "location_uri" // String
private const val SYNC_KEY = "sync" // Boolean
private const val OPTIONS_KEY = "options" // BooleanArray
// KMK -->
private const val V2_SYNC_KEY = "v2_sync"
private const val V2_CURSOR_KEY = "v2_cursor"
private const val V2_FULL_REQUESTED_KEY = "v2_full_requested"
private const val V2_LAST_PUSHED_KEY = "v2_last_pushed"
private const val V2_LAST_FULL_KEY = "v2_last_full"
// KMK <--
