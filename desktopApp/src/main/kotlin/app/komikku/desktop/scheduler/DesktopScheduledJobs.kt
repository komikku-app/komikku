package app.komikku.desktop.scheduler

import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.updater.DesktopAppUpdateChecker
import app.komikku.desktop.updater.appUpdateCheckTask
import app.komikku.shared.library.LibraryUpdateRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.time.Duration.Companion.hours

/**
 * KMK: the desktop's scheduled work on Android's preferences (same keys, so a restored backup carries the settings):
 *
 * - **Library update** — Android `LibraryUpdateJob.setupTask`: every `autoUpdateInterval()` hours (0 = never) an update
 *   of the whole library (`Target.All`: update categories, smart-update restrictions, like the periodic job). Due from
 *   the scheduler's own [DesktopSchedulerPreferences.lastScheduledLibraryUpdate] (Android's periodic work keeps its own
 *   period), so manual and partial updates do not postpone it; they still set `lastUpdatedTimestamp()` for the updates
 *   tab. A running update (manual or scheduled) makes it retry 10 minutes later (Android: `Result.retry()` while the
 *   manual worker runs). The device restrictions (Wi-Fi, unmetered, charging) and Android's "battery not low" have no
 *   reliable JVM counterpart and are not applied on desktop; the preference is kept untouched.
 * - **Automatic backup** — Android `BackupCreateJob.setupTask`: every `backupInterval()` hours (0 = off) an automatic
 *   backup (`DesktopBackupService.createAutomatic`: the storage location's `autobackup` folder, at most four files, sets
 *   `lastAutoBackupTimestamp()`); due from that timestamp. While another backup job runs (a manual backup or a restore)
 *   it retries 10 minutes later (Android retries while a restore runs). Cancelled by the user, it counts as failed and
 *   waits a whole interval (the scheduler's rule for work cancelled on its own).
 *
 * - **App update check** (P6, only with [appUpdateChecker]) — Android `AppUpdateJob`: every 3 days while the updater
 *   is enabled (see `appUpdateCheckTask`).
 *
 * When the scheduler stops (app exit), each task stops the work it started and waits for its end.
 */
fun desktopScheduler(
    libraryPreferences: LibraryPreferences,
    backupPreferences: BackupPreferences,
    schedulerPreferences: DesktopSchedulerPreferences,
    libraryUpdateRunner: LibraryUpdateRunner,
    backupJobs: DesktopBackupJobs,
    appUpdateChecker: DesktopAppUpdateChecker? = null,
    now: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): DesktopScheduler = DesktopScheduler(
    scope = scope,
    tasks = listOfNotNull(
        libraryUpdateTask(libraryPreferences, schedulerPreferences, libraryUpdateRunner, now),
        automaticBackupTask(backupPreferences, backupJobs),
        appUpdateChecker?.let { appUpdateCheckTask(it, schedulerPreferences, now) },
    ),
    now = now,
    wakeUps = merge(
        libraryPreferences.autoUpdateInterval().changes().drop(1).map {},
        backupPreferences.backupInterval().changes().drop(1).map {},
    ),
)

const val LIBRARY_UPDATE_TASK = "library update"
const val AUTOMATIC_BACKUP_TASK = "automatic backup"

fun libraryUpdateTask(
    preferences: LibraryPreferences,
    schedulerPreferences: DesktopSchedulerPreferences,
    runner: LibraryUpdateRunner,
    now: () -> Long = System::currentTimeMillis,
) = DesktopScheduler.Task(
    name = LIBRARY_UPDATE_TASK,
    intervalMillis = { preferences.autoUpdateInterval().get().hours.inWholeMilliseconds },
    lastRun = { schedulerPreferences.lastScheduledLibraryUpdate().get() },
    run = {
        if (runner.start(LibraryUpdateRunner.Target.All)) {
            schedulerPreferences.lastScheduledLibraryUpdate().set(now())
            try {
                // `start` replaced the progress synchronously, so this waits for this run's end (also when the user
                // cancels it: the progress then ends as cancelled).
                runner.progress.first { it?.finished == true }
            } catch (e: CancellationException) {
                // Only the scheduler stopping (app exit) gets here: stop the update and wait for its end.
                withContext(NonCancellable) {
                    runner.cancel()
                    runner.progress.first { it?.finished != false }
                }
                throw e
            }
            DesktopScheduler.Outcome.Done
        } else {
            DesktopScheduler.Outcome.Busy
        }
    },
)

fun automaticBackupTask(preferences: BackupPreferences, backupJobs: DesktopBackupJobs) = DesktopScheduler.Task(
    name = AUTOMATIC_BACKUP_TASK,
    intervalMillis = { preferences.backupInterval().get().hours.inWholeMilliseconds },
    lastRun = { preferences.lastAutoBackupTimestamp().get() },
    run = task@{
        val run = backupJobs.createAutomatic() ?: return@task DesktopScheduler.Outcome.Busy
        try {
            if (run.await()) DesktopScheduler.Outcome.Done else DesktopScheduler.Outcome.Failed
        } catch (e: CancellationException) {
            // The scheduler stopped (app exit): stop the backup and wait until it cleaned up. When the backup itself was
            // cancelled (the data screen) and the scheduler still runs, the scheduler counts this as a failure.
            if (!currentCoroutineContext().isActive) withContext(NonCancellable) { run.cancelAndJoin() }
            throw e
        }
    },
)
