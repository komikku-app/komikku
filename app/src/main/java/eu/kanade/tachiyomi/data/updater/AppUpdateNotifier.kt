package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

internal class AppUpdateNotifier(private val context: Context) {

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_APP_UPDATE) {
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
    }

    /**
     * Call to show notification.
     *
     * @param id id of the notification channel.
     */
    internal fun NotificationCompat.Builder.show(id: Int = Notifications.ID_APP_UPDATER) {
        context.notify(id, build())
    }

    fun cancel() {
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATER)
    }

    /**
     * Create a notification to prompt user there is new update, with action to:
     * - Download update [AppUpdateDownloadJob.start]
     * - Show Github's release notes
     */
    fun promptUpdate(release: Release) {
        val updateIntent = NotificationReceiver.downloadAppUpdatePendingBroadcast(
            context,
            release.downloadLink,
            release.version,
        )

        val releaseIntent = Intent(Intent.ACTION_VIEW, release.releaseLink.toUri()).run {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                context,
                release.hashCode(),
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.update_check_notification_update_available))
            setContentText(release.version)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentIntent(updateIntent)

            clearActions()
            addAction(
                android.R.drawable.stat_sys_download_done,
                context.stringResource(MR.strings.action_download),
                updateIntent,
            )
            addAction(
                R.drawable.ic_info_24dp,
                context.stringResource(MR.strings.whats_new),
                releaseIntent,
            )
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download starts.
     *
     * @param title tile of notification.
     */
    fun onDownloadStarted(title: String? = null): NotificationCompat.Builder {
        with(notificationBuilder) {
            title?.let { setContentTitle(title) }
            setContentText(context.stringResource(MR.strings.update_check_notification_download_in_progress))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.cancelDownloadAppUpdatePendingBroadcast(context),
            )
        }

        // KMK -->
        // Avoid calling show() before returning builder for ForegroundInfo.
        // Calling show() here can cause duplicate notifications, as setForegroundSafely will display the notification using the returned builder.
        // notificationBuilder.show()
        // KMK <--

        return notificationBuilder
    }

    /**
     * Call when apk download progress changes.
     *
     * @param progress progress of download (xx%/100).
     */
    fun onProgressChange(progress: Int) {
        with(notificationBuilder) {
            setProgress(100, progress, false)
            setOnlyAlertOnce(true)
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download is finished.
     *
     * @param uri path location of apk.
     */
    fun promptInstall(uri: Uri, title: String? = null) {
        val installIntent = NotificationHandler.installApkPendingActivity(context, uri)
        with(notificationBuilder) {
            // KMK -->
            title?.let { setContentTitle(title) }
            // KMK <--
            setContentText(context.stringResource(MR.strings.update_check_notification_download_complete))
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)
            setContentIntent(installIntent)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_system_update_alt_white_24dp,
                context.stringResource(MR.strings.action_install),
                installIntent,
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_PROMPT),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_PROMPT)
    }

    /**
     * Call when apk download throws a error
     *
     * @param url web location of apk to download.
     */
    fun onDownloadError(
        url: String,
        // KMK -->
        error: String? = null,
        // KMK <--
    ) {
        with(notificationBuilder) {
            setContentText(
                context.stringResource(MR.strings.update_check_notification_download_error) +
                    // KMK -->
                    (": $error".takeIf { error != null } ?: ""),
                // KMK <--
            )
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)

            clearActions()
            addAction(
                R.drawable.ic_refresh_24dp,
                context.stringResource(MR.strings.action_retry),
                NotificationReceiver.downloadAppUpdatePendingBroadcast(context, url),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_ERROR),
            )
            // KMK -->
            addAction(
                R.drawable.ic_get_app_24dp,
                context.stringResource(KMR.strings.manual_download),
                NotificationHandler.openUrl(
                    context,
                    url,
                ),
            )
            // KMK <--
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_ERROR)
    }

    // KMK -->
    fun onInstalling(uri: Uri) {
        val installIntent = NotificationHandler.installApkPendingActivity(context, uri)
        with(notificationBuilder) {
            setContentText(context.stringResource(MR.strings.ext_installing))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setProgress(0, 0, true)
            setOnlyAlertOnce(true)
            clearActions()
            addAction(
                R.drawable.ic_system_update_alt_white_24dp,
                context.stringResource(KMR.strings.action_manual_install),
                installIntent,
            )
            show(Notifications.ID_APP_INSTALL)
        }
    }

    /** Call when apk download is finished. */
    fun onInstallFinished() {
        with(notificationBuilder) {
            setContentTitle(context.stringResource(KMR.strings.update_completed))
            setContentText(context.stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME))
            setSmallIcon(R.drawable.ic_komikku)
            setAutoCancel(true)
            setOngoing(false)
            setProgress(0, 0, false)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentIntent(pendingIntent)
            clearActions()
            addAction(
                R.drawable.ic_launch,
                context.stringResource(KMR.strings.open),
                pendingIntent,
            )
            addReleasePageAction()
            show(Notifications.ID_APP_INSTALLED)
        }
    }

    private fun NotificationCompat.Builder.addReleasePageAction() {
        releasePageUrl?.let { releaseUrl ->
            val releaseIntent = Intent(Intent.ACTION_VIEW, releaseUrl.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            addAction(
                R.drawable.ic_info_24dp,
                context.stringResource(KMR.strings.release_page),
                PendingIntent.getActivity(
                    context,
                    releaseUrl.hashCode(),
                    releaseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    fun onInstallError(uri: Uri, title: String?) {
        with(notificationBuilder) {
            title?.let { setContentTitle(title) }
            setContentText(context.stringResource(KMR.strings.could_not_install_update))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOnlyAlertOnce(false)
            setAutoCancel(false)
            setProgress(0, 0, false)
            clearActions()
            // Retry action
            addAction(
                R.drawable.ic_refresh_24dp,
                context.stringResource(MR.strings.action_retry),
                NotificationHandler.installApkPendingActivity(context, uri),
            )
            // Cancel action
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_ERROR),
            )
            addReleasePageAction()
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_ERROR)
    }

    fun cancelInstallNotification() {
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_INSTALL)
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATE_PROMPT)
    }

    companion object {
        var releasePageUrl: String? = null
    }
    // KMK <--
}
