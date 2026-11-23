package app.komikku.desktop.scheduler

import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.shared.library.LibraryUpdateRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.time.Duration.Companion.hours

/**
 * KMK: the desktop's scheduled work on Android's preferences (same keys, so a restored backup carries the settings):
 *
 * - **Library update** — Android `LibraryUpdateJob.setupTask`: every `autoUpdateInterval()` hours (0 = never) an update
 *   of the whole library (`Target.All`: update categories, smart-update restrictions, like the periodic job). Due from
 *   `lastUpdatedTimestamp()`, which every library update sets when it starts (as Android's job does), so a manual
 *   update also postpones the next scheduled one. A running update (manual or scheduled) makes it retry 10 minutes
 *   later (Android: `Result.retry()` while the manual worker runs). The device restrictions (Wi-Fi, unmetered,
 *   charging) and Android's "battery not low" have no reliable JVM counterpart and are not applied on desktop; the
 *   preference is kept untouched.
 * - **Automatic backup** — Android `BackupCreateJob.setupTask`: every `backupInterval()` hours (0 = off) the borrowed
 *   `BackupCreator` in automatic mode (the storage location's `autobackup` folder, at most four files, sets
 *   `lastAutoBackupTimestamp()`); due from that timestamp. While another backup job runs (a manual backup or a restore)
 *   it retries 10 minutes later (Android retries while a restore runs).
 */
fun desktopScheduler(
    libraryPreferences: LibraryPreferences,
    backupPreferences: BackupPreferences,
    libraryUpdateRunner: LibraryUpdateRunner,
    backupJobs: DesktopBackupJobs,
    now: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): DesktopScheduler = DesktopScheduler(
    scope = scope,
    tasks = listOf(
        libraryUpdateTask(libraryPreferences, libraryUpdateRunner),
        automaticBackupTask(backupPreferences, backupJobs),
    ),
    now = now,
    wakeUps = merge(
        libraryPreferences.autoUpdateInterval().changes().drop(1).map {},
        backupPreferences.backupInterval().changes().drop(1).map {},
    ),
)

const val LIBRARY_UPDATE_TASK = "library update"
const val AUTOMATIC_BACKUP_TASK = "automatic backup"

fun libraryUpdateTask(preferences: LibraryPreferences, runner: LibraryUpdateRunner) = DesktopScheduler.Task(
    name = LIBRARY_UPDATE_TASK,
    intervalMillis = { preferences.autoUpdateInterval().get().hours.inWholeMilliseconds },
    lastRun = { preferences.lastUpdatedTimestamp().get() },
    run = {
        if (runner.start(LibraryUpdateRunner.Target.All)) {
            // `start` replaced the progress synchronously, so this waits for this run's end.
            runner.progress.first { it?.finished == true }
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
    run = {
        val run = backupJobs.createAutomatic()
        when {
            run == null -> DesktopScheduler.Outcome.Busy
            run.await() -> DesktopScheduler.Outcome.Done
            else -> DesktopScheduler.Outcome.Failed
        }
    },
)
