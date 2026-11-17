package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.workManager
import exh.util.WorkerUtil
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// KMK -->
/**
 * Once a day, at the time chosen by the user, tells which titles of the library are expected to release today.
 *
 * It uses the release forecast that the library update already computes, nothing is fetched from the network.
 * The job is one-shot and schedules the next day by itself, so that it stays on time across daylight
 * saving changes, which a 24 hours periodic job does not.
 */
class ReleaseReminderJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<LibraryPreferences>()
        if (!preferences.releaseReminder().get()) return Result.success()

        try {
            val upcoming = Injekt.get<GetUpcomingManga>().subscribe().first()
            val titles = titlesDueOn(LocalDate.now(), upcoming)
            if (titles.isNotEmpty()) {
                showNotification(titles)
            } else {
                context.cancelNotification(Notifications.ID_RELEASE_REMINDER)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to show the release reminder" }
        } finally {
            // Always plan the next day, whatever happened today
            setupTask(context)
        }
        return Result.success()
    }

    private fun showNotification(titles: List<String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Constants.SHORTCUT_UPDATES
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        context.notify(Notifications.ID_RELEASE_REMINDER, Notifications.CHANNEL_RELEASE_REMINDER) {
            setSmallIcon(R.drawable.ic_komikku)
            setContentTitle(context.stringResource(KMR.strings.release_reminder_title))
            setContentText(summary(context, titles))
            setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(titles.take(MAX_LISTED).joinToString("\n") { it.chop(TITLE_MAX_LENGTH) }),
            )
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setCategory(NotificationCompat.CATEGORY_REMINDER)
        }
    }

    companion object {
        private const val WORK_NAME = "ReleaseReminder"
        private const val MAX_SUMMARY = 3
        private const val MAX_LISTED = 10
        private const val TITLE_MAX_LENGTH = 45

        /** Titles whose next chapter is expected on [day], in the order of the forecast. */
        internal fun titlesDueOn(day: LocalDate, upcoming: List<Manga>, zone: ZoneId = ZoneId.systemDefault()): List<String> {
            return upcoming
                .filter { manga ->
                    manga.expectedNextUpdate?.atZone(zone)?.toLocalDate() == day
                }
                .map { it.title }
        }

        /** The time to wait before the next [hour] o'clock, always more than zero and at most one day. */
        internal fun delayUntilNext(hour: Int, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Duration {
            var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now.atZone(zone), next.atZone(zone))
        }

        private fun summary(context: Context, titles: List<String>): String {
            val shown = titles.take(MAX_SUMMARY).joinToString(", ") { it.chop(TITLE_MAX_LENGTH) }
            val more = titles.size - MAX_SUMMARY
            return if (more > 0) {
                context.stringResource(KMR.strings.release_reminder_more, shown, more)
            } else {
                shown
            }
        }

        /** Plans the next reminder, or cancels it when reminders are off. */
        fun setupTask(context: Context) {
            val preferences = Injekt.get<LibraryPreferences>()
            if (!preferences.releaseReminder().get()) {
                context.workManager.cancelUniqueWork(WORK_NAME)
                context.cancelNotification(Notifications.ID_RELEASE_REMINDER)
                return
            }

            val delay = delayUntilNext(preferences.releaseReminderHour().get(), LocalDateTime.now())
            val request = OneTimeWorkRequestBuilder<ReleaseReminderJob>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            context.workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        suspend fun isScheduled(context: Context): Boolean {
            return WorkerUtil.isPeriodicJobScheduled(context, WORK_NAME)
        }
    }
}
// KMK <--
