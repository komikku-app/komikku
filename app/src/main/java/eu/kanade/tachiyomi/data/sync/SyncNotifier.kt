package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SyncNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    // KMK -->
    private val syncStatus: SyncStatus = Injekt.get()
    // KMK <--

    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_LIBRARY,
    ) {
        setSmallIcon(R.drawable.ic_komikku)
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_LIBRARY,
    ) {
        setSmallIcon(R.drawable.ic_komikku)
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        setAutoCancel(false)
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    suspend fun showSyncProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
    ): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.getString(R.string.syncing_library))

            if (!preferences.hideNotificationContent().get()) {
                setContentText(content)
            }

            setProgress(maxAmount, progress, true)
            setOnlyAlertOnce(true)
            // KMK -->
            syncStatus.updateProgress(progress.toFloat() / maxAmount)
            // KMK <--

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.getString(R.string.action_cancel),
                NotificationReceiver.cancelSyncPendingBroadcast(context, Notifications.ID_SYNC_PROGRESS),
            )
        }

        // KMK -->
        // Avoid calling show() before returning builder for ForegroundInfo.
        // Calling show() here can cause duplicate notifications, as setForegroundSafely will display the notification using the returned builder.
        // builder.show(Notifications.ID_SYNC_PROGRESS)
        // KMK <--

        return builder
    }

    fun showSyncError(error: String?) {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.sync_error))
            setContentText(error)

            show(Notifications.ID_SYNC_COMPLETE)
        }
    }

    fun showSyncSuccess(message: String?) {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.sync_complete))
            setContentText(message)

            show(Notifications.ID_SYNC_COMPLETE)
        }
    }
}
