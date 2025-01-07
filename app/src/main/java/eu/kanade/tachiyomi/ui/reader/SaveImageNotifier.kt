package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Class used to show BigPictureStyle notifications
 */
class SaveImageNotifier(private val context: Context) {

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_COMMON) {
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
    }
    private val notificationId: Int = Notifications.ID_DOWNLOAD_IMAGE

    /**
     * Called when image download/copy is complete.
     *
     * @param uri image file containing downloaded page image.
     */
    fun onComplete(uri: Uri) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(720, 1280)
            .target(
                onSuccess = { showCompleteNotification(uri, it.asDrawable(context.resources).getBitmapOrNull()) },
                onError = { onError(null) },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /**
     * Clears the notification message.
     */
    fun onClear() {
        context.cancelNotification(notificationId)
    }

    // KMK -->
    /**
     * Bitmap of the app for notifications.
     */
    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.komikku)
    }
    // KMK <--

    /**
     * Called on error while downloading image.
     * @param error string containing error information.
     */
    fun onError(error: String?) {
        // Create notification
        with(notificationBuilder) {
            // KMK -->
            setLargeIcon(notificationBitmap)
            // KMK <--
            setContentTitle(context.stringResource(MR.strings.download_notifier_title_error))
            setContentText(error ?: context.stringResource(MR.strings.unknown_error))
            setSmallIcon(android.R.drawable.ic_menu_report_image)
        }
        updateNotification()
    }

    private fun showCompleteNotification(uri: Uri, image: Bitmap?) {
        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.picture_saved))
            setSmallIcon(R.drawable.ic_photo_24dp)
            image?.let {
                setStyle(
                    NotificationCompat.BigPictureStyle()
                        // KMK -->
                        .bigLargeIcon(notificationBitmap)
                        .setBigContentTitle(context.stringResource(MR.strings.picture_saved))
                        // KMK <--
                        .bigPicture(it),
                )
                setLargeIcon(image)
            }
            setAutoCancel(true)

            // Clear old actions if they exist
            clearActions()

            setContentIntent(NotificationHandler.openImagePendingActivity(context, uri))
            // Share action
            addAction(
                R.drawable.ic_share_24dp,
                context.stringResource(MR.strings.action_share),
                NotificationReceiver.shareImagePendingBroadcast(context, uri),
            )

            updateNotification()
        }
    }

    private fun updateNotification() {
        // Displays the progress bar on notification
        context.notify(notificationId, notificationBuilder.build())
    }
}
