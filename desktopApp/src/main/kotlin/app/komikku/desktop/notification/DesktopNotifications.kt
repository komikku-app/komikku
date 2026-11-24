package app.komikku.desktop.notification

import android.content.Context
import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.shared.download.DownloadEvent
import app.komikku.shared.library.LibraryUpdateProgress
import app.komikku.shared.library.LibraryUpdateRunner
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.lang.chop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.text.NumberFormat
import java.util.concurrent.TimeUnit

/**
 * P17: Android's notifiers on desktop — what `LibraryUpdateNotifier`, `DownloadNotifier`, `BackupNotifier` and
 * `AppUpdateNotifier` post, with their texts, handed to a [DesktopNotifier] (the system tray):
 *
 * - a library update that ended (not cancelled; not a single entry's refresh, which Android's manga screen does without
 *   the job): "New chapters found" with the entry (one) or the count and the entries (several), "N updates failed"; while
 *   it runs, Android's progress title as the tray status;
 * - the downloader's errors and warnings (pause and completion post nothing, like Android's dismissed progress);
 * - a backup created or failed and a restore completed or failed (the screen's own jobs: the automatic backup reports
 *   nothing, like Android's `BackupCreateJob` for automatic backups);
 * - an app update found (P6's check), once per version.
 *
 * Entry titles (and a download error's chapter) are left out while Android's "Hide notification content" is on **or the
 * app is locked** ([isLocked], P16): the desktop shows notifications over every window, so a locked app does not name its
 * entries (Android only follows the preference; its downloader errors name the entry even then, the desktop does not).
 */
class DesktopNotifications(
    private val context: Context,
    private val notifier: DesktopNotifier,
    private val securityPreferences: SecurityPreferences,
    private val isLocked: () -> Boolean,
    private val libraryUpdates: StateFlow<LibraryUpdateProgress?>,
    private val downloadEvents: Flow<DownloadEvent>,
    private val backupStatus: StateFlow<DesktopBackupJobs.Status>,
    private val appUpdates: StateFlow<Release?>,
) {

    private val hideContent: Boolean get() = securityPreferences.hideNotificationContent().get() || isLocked()

    private val percent = NumberFormat.getPercentInstance()

    /** Follows the app's work in [scope] until it is cancelled. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        launch { followLibraryUpdates() }
        launch { downloadEvents.collect(::onDownloadEvent) }
        launch { followBackups() }
        launch { appUpdates.filterNotNull().distinctUntilChangedBy { it.version }.collect(::onAppUpdate) }
    }

    private suspend fun followLibraryUpdates() {
        // The run notified last: a finished progress stays in the flow until the next update starts. Review fix (P17):
        // by its run id, not by identity — a state flow conflates equal values, and a fast second run's finished progress
        // can equal the first one's except for its id (`LibraryUpdateProgress.runId`)
        var notified: Long? = libraryUpdates.value?.takeIf { it.finished }?.runId
        libraryUpdates.collect { progress ->
            when {
                progress == null -> notifier.status(null)
                !progress.finished -> notifier.status(
                    context.stringResource(
                        MR.strings.notification_updating_progress,
                        percent.format(if (progress.total == 0) 0f else progress.done.toFloat() / progress.total),
                    ),
                )
                progress.runId != notified -> {
                    notified = progress.runId
                    notifier.status(null)
                    onLibraryUpdateFinished(progress)
                }
            }
        }
    }

    private fun onLibraryUpdateFinished(progress: LibraryUpdateProgress) {
        if (progress.cancelled || progress.target is LibraryUpdateRunner.Target.Single) return
        val hide = hideContent
        if (progress.updated.isNotEmpty()) {
            val titles = progress.updated.map { (manga, _) -> manga.title.chop(TITLE_MAX_LENGTH) }
            val text = if (progress.updated.size == 1 && !hide) {
                titles.single()
            } else {
                val summary = context.pluralStringResource(
                    MR.plurals.notification_new_chapters_summary,
                    progress.updated.size,
                    progress.updated.size,
                )
                if (hide) {
                    summary
                } else {
                    (listOf(summary) + titles.take(MAX_TITLES) + listOfNotNull("…".takeIf { titles.size > MAX_TITLES }))
                        .joinToString("\n")
                }
            }
            notifier.show(
                DesktopNotification(
                    channel = DesktopNotification.Channel.NewChapters,
                    title = context.stringResource(MR.strings.notification_new_chapters),
                    text = text,
                ),
            )
        }
        if (progress.failed.isNotEmpty()) {
            notifier.show(
                DesktopNotification(
                    channel = DesktopNotification.Channel.LibraryErrors,
                    title = context.pluralStringResource(MR.plurals.notification_update_error, progress.failed.size, progress.failed.size),
                    text = context.stringResource(KMR.strings.desktop_notification_open),
                    level = DesktopNotification.Level.Warning,
                ),
            )
        }
    }

    private fun onDownloadEvent(event: DownloadEvent) {
        val downloader = context.stringResource(MR.strings.download_notifier_downloader_title)
        when (event) {
            is DownloadEvent.Error -> notifier.show(
                DesktopNotification(
                    channel = DesktopNotification.Channel.Downloader,
                    // Review fix (P17): the entry and the chapter joined as the downloader's toast does (no "null")
                    title = event.mangaTitle?.takeUnless { hideContent }
                        ?.let { title -> listOfNotNull(title, event.chapter).joinToString(": ") }
                        ?: downloader,
                    text = event.message ?: context.stringResource(MR.strings.download_notifier_unknown_error),
                    level = DesktopNotification.Level.Error,
                ),
            )
            is DownloadEvent.Warning -> notifier.show(
                DesktopNotification(
                    channel = DesktopNotification.Channel.Downloader,
                    title = downloader,
                    text = event.message,
                    level = DesktopNotification.Level.Warning,
                ),
            )
            DownloadEvent.Completed, DownloadEvent.Paused -> {}
        }
    }

    private suspend fun followBackups() {
        // A result already there when the app starts following is not news. Review fix (P17): each result instance is
        // notified once — an automatic backup sets `Running` and then puts the previous status object back
        // (`DesktopBackupJobs.createAutomatic`), which must not notify that result again
        var notified: DesktopBackupJobs.Status = backupStatus.value
        backupStatus.collect { status ->
            if (status === notified) return@collect
            if (status is DesktopBackupJobs.Status.Created || status is DesktopBackupJobs.Status.Restored || status is DesktopBackupJobs.Status.Failed) {
                notified = status
            }
            when (status) {
                is DesktopBackupJobs.Status.Created -> notifier.show(
                    DesktopNotification(
                        channel = DesktopNotification.Channel.Backup,
                        title = context.stringResource(MR.strings.backup_created),
                        text = status.file.name,
                    ),
                )
                is DesktopBackupJobs.Status.Restored -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(status.timeMillis)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(status.timeMillis) - TimeUnit.MINUTES.toSeconds(minutes)
                    val time = context.stringResource(MR.strings.restore_duration, minutes, seconds)
                    notifier.show(
                        DesktopNotification(
                            channel = DesktopNotification.Channel.Restore,
                            title = context.stringResource(MR.strings.restore_completed),
                            text = context.pluralStringResource(
                                MR.plurals.restore_completed_message,
                                status.errors.size,
                                time,
                                status.errors.size,
                            ),
                        ),
                    )
                }
                is DesktopBackupJobs.Status.Failed -> notifier.show(
                    DesktopNotification(
                        channel = when (status.kind) {
                            DesktopBackupJobs.Kind.Create -> DesktopNotification.Channel.Backup
                            DesktopBackupJobs.Kind.Restore -> DesktopNotification.Channel.Restore
                        },
                        title = context.stringResource(
                            when (status.kind) {
                                DesktopBackupJobs.Kind.Create -> MR.strings.creating_backup_error
                                DesktopBackupJobs.Kind.Restore -> MR.strings.restoring_backup_error
                            },
                        ),
                        text = status.message.orEmpty(),
                        level = DesktopNotification.Level.Error,
                    ),
                )
                DesktopBackupJobs.Status.Idle, is DesktopBackupJobs.Status.Running, is DesktopBackupJobs.Status.Cancelled -> {}
            }
        }
    }

    private fun onAppUpdate(release: Release) {
        notifier.show(
            DesktopNotification(
                channel = DesktopNotification.Channel.AppUpdate,
                title = context.stringResource(MR.strings.update_check_notification_update_available),
                text = release.version,
            ),
        )
    }

    companion object {
        /** Android's `NOTIF_TITLE_MAX_LEN`. */
        private const val TITLE_MAX_LENGTH = 45

        /** Titles listed in a "new chapters" notification (a tray balloon shows a few lines). */
        private const val MAX_TITLES = 5
    }
}
