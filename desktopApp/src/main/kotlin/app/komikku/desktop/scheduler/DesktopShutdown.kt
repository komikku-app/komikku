package app.komikku.desktop.scheduler

import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.shared.library.LibraryUpdateRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.logcat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: app exit (the window closed): stops the scheduler and the work in progress, the scheduled one and the manual
 * one (a backup / restore, a library update), and waits up to [timeout] for it to end, so a backup is not cut off
 * halfway by the JVM exiting (its temporary file is removed; the previous backups are untouched). Returns whether
 * everything ended in time.
 */
suspend fun stopDesktopWork(
    scheduler: DesktopScheduler,
    backupJobs: DesktopBackupJobs,
    libraryUpdateRunner: LibraryUpdateRunner,
    timeout: Duration = 10.seconds,
): Boolean {
    val ended = withTimeoutOrNull(timeout) {
        scheduler.stop()
        backupJobs.cancelAndJoin()
        libraryUpdateRunner.cancel()
        libraryUpdateRunner.progress.first { it?.finished != false }
        true
    } ?: false
    if (!ended) logcat("DesktopShutdown", LogPriority.WARN) { "Work still running $timeout after exit was requested" }
    return ended
}
