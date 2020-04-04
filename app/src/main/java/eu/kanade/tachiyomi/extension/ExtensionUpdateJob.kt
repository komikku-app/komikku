package eu.kanade.tachiyomi.extension

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.util.system.notification
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

class ExtensionUpdateJob : Job() {

    override fun onRunJob(params: Params): Result {
        GlobalScope.launch(Dispatchers.IO) {
            val pendingUpdates = ExtensionGithubApi().checkForUpdates(context)
            if (pendingUpdates.isNotEmpty()) {
                val names = pendingUpdates.map { it.name }
                val preferences: PreferencesHelper by injectLazy()
                preferences.extensionUpdatesCount().set(pendingUpdates.size)
                NotificationManagerCompat.from(context).apply {
                    notify(Notifications.ID_UPDATES_TO_EXTS,
                            context.notification(Notifications.CHANNEL_UPDATES_TO_EXTS) {
                                setContentTitle(
                                        context.resources.getQuantityString(
                                                R.plurals.update_check_notification_ext_updates, names
                                                .size, names.size
                                        )
                                )
                                val extNames = if (names.size > 5) {
                                    "${names.take(4).joinToString(", ")}, " +
                                            context.resources.getQuantityString(
                                                    R.plurals.update_check_notification_ext_updates,
                                                    (names.size - 4), (names.size - 4)
                                            )
                                } else names.joinToString(", ")
                                setContentText(extNames)
                                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
                                setSmallIcon(R.drawable.ic_extension_update)
                                color = ContextCompat.getColor(context, R.color.colorAccentLight)
                                setContentIntent(
                                        NotificationReceiver.openExtensionsPendingActivity(
                                                context
                                        )
                                )
                                setAutoCancel(true)
                            })
                }
            }
        }
        return Result.SUCCESS
    }

    companion object {
        const val TAG = "ExtensionUpdate"

        fun setupTask() {
            JobRequest.Builder(TAG).setPeriodic(TimeUnit.HOURS.toMillis(12),
                    TimeUnit.HOURS.toMillis(2))
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setRequirementsEnforced(true)
                    .setUpdateCurrent(true)
                    .build().schedule()
        }

        fun cancelTask() {
            JobManager.instance().cancelAllForTag(TAG)
        }
    }
}
