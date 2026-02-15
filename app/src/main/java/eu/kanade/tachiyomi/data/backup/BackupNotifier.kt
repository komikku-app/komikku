package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.BackupRestoreStatus
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupNotifier(private val context: Context) {

    private val lock = Mutex()

    private val preferences: SecurityPreferences by injectLazy()

    // KMK -->
    private val backupRestoreStatus: BackupRestoreStatus = Injekt.get()
    // KMK <--

    private val largeIcon by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.komikku)
    }

    private val completeNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(largeIcon)
            setAutoCancel(false)
        }
    }

    private fun newProgressBuilder(): NotificationCompat.Builder {
        return context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(largeIcon)
            setAutoCancel(false)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
    }

    private var progressNotificationBuilder: NotificationCompat.Builder? = null

    internal fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun showBackupProgress(): NotificationCompat.Builder {
        val builder = newProgressBuilder()
            .setContentTitle(context.stringResource(MR.strings.creating_backup))
            .setProgress(0, 0, true)

        // KMK -->
        // Avoid calling show() before returning builder for ForegroundInfo.
        // Calling show() here can cause duplicate notifications, as setForegroundSafely will display the notification using the returned builder.
        // builder.show(Notifications.ID_BACKUP_PROGRESS)
        // KMK <--

        return builder
    }

    fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.creating_backup_error))
            setContentText(error)

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showBackupComplete(file: UniFile) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.backup_created))
            setContentText(file.displayablePath)

            clearActions()
            addAction(
                R.drawable.ic_share_24dp,
                context.stringResource(MR.strings.action_share),
                NotificationReceiver.shareBackupPendingActivity(context, file.uri),
            )

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    suspend fun showRestoreProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
        sync: Boolean = false,
    ): NotificationCompat.Builder {
        // KMK -->
        backupRestoreStatus.updateProgress(progress.toFloat() / maxAmount)
        // KMK <--

        lock.withLock {
            val builder = progressNotificationBuilder ?: newProgressBuilder().also { progressNotificationBuilder = it }
            with(builder) {
                setContentTitle(
                    if (sync) {
                        context.stringResource(MR.strings.syncing_library)
                    } else {
                        context.stringResource(MR.strings.restoring_backup)
                    },
                )
                setProgress(maxAmount, progress, false)
                setOnlyAlertOnce(true)
                clearActions()
                addAction(
                    R.drawable.ic_close_24dp,
                    context.stringResource(MR.strings.action_cancel),
                    NotificationReceiver.cancelRestorePendingBroadcast(context, Notifications.ID_RESTORE_PROGRESS),
                )
                if (!preferences.hideNotificationContent().get() && content.isNotEmpty()) {
                    setContentText(content)
                } else if (preferences.hideNotificationContent().get()) {
                    setContentText(null)
                }
                show(Notifications.ID_RESTORE_PROGRESS)
            }
            return builder
        }
    }

    fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.restoring_backup_error))
            setContentText(error)

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }

    fun showRestoreComplete(
        time: Long,
        errorCount: Int,
        path: String?,
        file: String?,
        sync: Boolean,
    ) {
        val contentTitle = if (sync) {
            context.stringResource(MR.strings.library_sync_complete)
        } else {
            context.stringResource(MR.strings.restore_completed)
        }

        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        val timeString = context.stringResource(
            MR.strings.restore_duration,
            TimeUnit.MILLISECONDS.toMinutes(time),
            TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(time),
            ),
        )

        with(completeNotificationBuilder) {
            setContentTitle(contentTitle)
            setContentText(
                context.pluralStringResource(
                    MR.plurals.restore_completed_message,
                    errorCount,
                    timeString,
                    errorCount,
                ),
            )

            clearActions()
            if (errorCount > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
                val destFile = File(path, file)
                val uri = destFile.getUriCompat(context)

                val errorLogIntent = NotificationReceiver.openErrorLogPendingActivity(context, uri)
                setContentIntent(errorLogIntent)
                addAction(
                    R.drawable.ic_folder_24dp,
                    context.stringResource(MR.strings.action_show_errors),
                    errorLogIntent,
                )
            }

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }
}
