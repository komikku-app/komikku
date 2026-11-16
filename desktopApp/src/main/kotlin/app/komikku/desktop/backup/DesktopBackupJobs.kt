package app.komikku.desktop.backup

import app.komikku.shared.backup.BackupProgressListener
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * KMK: app-lifetime runner for creating and restoring backups, the desktop counterpart of Android's
 * `BackupCreateJob` / `BackupRestoreJob` (WorkManager). The work runs in this object's own scope, so
 * leaving the "Data and storage" screen does not cancel it halfway; the screen observes [status]. One job
 * runs at a time. [cancel] stops the running job, which ends as [Status.Cancelled] (Android: the restore
 * notification's cancel action).
 */
class DesktopBackupJobs(
    private val createBackup: suspend (target: File, options: BackupOptions) -> File,
    private val restoreBackup: suspend (file: File, options: RestoreOptions, listener: BackupProgressListener) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    constructor(service: DesktopBackupService) : this(
        createBackup = { target, options -> service.create(target, options) },
        restoreBackup = { file, options, listener -> service.restore(file, options, listener) },
    )

    enum class Kind { Create, Restore }

    sealed interface Status {
        data object Idle : Status

        data class Running(val kind: Kind, val content: String = "", val progress: Int = 0, val max: Int = 0) : Status

        data class Created(val file: File) : Status

        data class Restored(val timeMillis: Long, val errors: List<String>, val log: File?) : Status

        data class Failed(val kind: Kind, val message: String?) : Status

        data class Cancelled(val kind: Kind) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var job: Job? = null

    /** Starts writing a backup of [options] to [target]; `false` when another job is running. */
    fun create(target: File, options: BackupOptions): Boolean = start(Kind.Create) {
        Status.Created(createBackup(target, options))
    }

    /** Starts restoring [file]; `false` when another job is running. */
    fun restore(file: File, options: RestoreOptions): Boolean = start(Kind.Restore) {
        var completed: Status.Restored? = null
        val listener = object : BackupProgressListener {
            override fun onRestoreProgress(content: String, progress: Int, max: Int) {
                _status.update { if (it is Status.Running && it.kind == Kind.Restore) Status.Running(Kind.Restore, content, progress, max) else it }
            }

            override fun onRestoreComplete(timeMillis: Long, errorCount: Int, errorLog: File?) {
                val errors = errorLog?.takeIf { it.isFile }?.readLines().orEmpty()
                completed = Status.Restored(timeMillis, errors, errorLog)
            }
        }
        restoreBackup(file, options, listener)
        completed ?: Status.Restored(0, emptyList(), null)
    }

    /** Cancels the running job (reported as [Status.Cancelled]). */
    @Synchronized
    fun cancel() {
        job?.cancel()
    }

    /** Clears a finished job's result (the screen dismissed it); a running job is left alone. */
    fun acknowledge() = _status.update { if (it is Status.Running) it else Status.Idle }

    @Synchronized
    private fun start(kind: Kind, block: suspend () -> Status): Boolean {
        if (job?.isActive == true) return false
        _status.value = Status.Running(kind)
        job = scope.launch {
            val result = try {
                block()
            } catch (_: CancellationException) {
                Status.Cancelled(kind)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Status.Failed(kind, e.message ?: e::class.simpleName)
            }
            _status.value = result
        }
        return true
    }
}
