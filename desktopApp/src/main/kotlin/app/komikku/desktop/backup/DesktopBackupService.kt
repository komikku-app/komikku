package app.komikku.desktop.backup

import android.app.Application
import android.net.Uri
import app.komikku.shared.backup.BackupProgressListener
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * KMK: desktop entry points for backups over the borrowed Android `BackupCreator` / `BackupRestorer` /
 * `BackupFileValidator` (same `.tachibk` protobuf format, so backups move between Android and desktop).
 * Android runs them in WorkManager jobs with notifications; on desktop the caller's coroutine runs them
 * and a [BackupProgressListener] receives what the notifications would show.
 */
class DesktopBackupService(
    private val app: Application,
    private val storagePreferences: StoragePreferences,
    private val storageManager: StorageManager,
) {

    /**
     * P3: Android's automatic backup (`BackupCreateJob` with `isAutoBackup`): the borrowed `BackupCreator` in automatic
     * mode writes a new file into the storage location's automatic backups folder, keeps the newest four and sets
     * `BackupPreferences.lastAutoBackupTimestamp()`. No notification, like Android.
     */
    suspend fun createAutomatic(options: BackupOptions = BackupOptions()): String = withIOContext {
        val dir = storageManager.getAutomaticBackupsDirectory()
            ?: throw IllegalStateException(app.stringResource(MR.strings.create_backup_file_error))
        BackupCreator(app, isAutoBackup = true).backup(dir.uri, options)
    }

    /** Android `BackupCreator.getFilename()` in `<storage folder>/backup` (no directory is created here). */
    fun defaultBackupFile(): File {
        val base = Uri.parse(storagePreferences.baseStorageDirectory().get()).path?.let(::File) ?: app.filesDir
        return File(File(base, "backup"), BackupCreator.getFilename())
    }

    /**
     * Writes a backup of [options] to [target] and validates it. The borrowed creator deletes its output
     * when it fails, so it writes a temporary file next to [target] that replaces [target] only on success
     * (an existing backup at [target] survives a failed run).
     */
    suspend fun create(
        target: File,
        options: BackupOptions = BackupOptions(),
        listener: BackupProgressListener = BackupProgressListener.None,
    ): File = withIOContext {
        val notifier = BackupNotifier(app, listener)
        val dir = target.absoluteFile.parentFile.apply { mkdirs() }
        val temp = File(dir, ".${target.name}.tmp")
        try {
            notifier.showBackupProgress()
            // The borrowed creator opens an existing document, like Android's storage access framework.
            temp.delete()
            temp.createNewFile()
            BackupCreator(app, isAutoBackup = false).backup(Uri.fromFile(temp), options)
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            notifier.showBackupComplete(UniFile.fromFile(target)!!)
            target
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Exception) {
            temp.delete()
            notifier.showBackupError(e.message)
            throw e
        }
    }

    /** Sources and trackers the backup needs that are missing here (Android: shown before restoring). */
    suspend fun validate(file: File): BackupFileValidator.Results = withIOContext {
        BackupFileValidator(app).validate(Uri.fromFile(file))
    }

    /**
     * Restores [file]; progress, completion (with the error log file) and failures go to [listener].
     * Cancellation is reported like Android's `BackupRestoreJob` ("Canceled restore") and rethrown.
     */
    suspend fun restore(
        file: File,
        options: RestoreOptions = RestoreOptions(),
        listener: BackupProgressListener = BackupProgressListener.None,
    ) = withIOContext {
        val notifier = BackupNotifier(app, listener)
        try {
            BackupRestorer(app, notifier, isSync = false).restore(Uri.fromFile(file), options)
        } catch (e: CancellationException) {
            notifier.showRestoreError(app.stringResource(MR.strings.restoring_backup_canceled))
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
            throw e
        }
    }
}
