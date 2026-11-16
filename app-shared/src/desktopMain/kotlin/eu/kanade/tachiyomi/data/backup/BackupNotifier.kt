package eu.kanade.tachiyomi.data.backup

import android.content.Context
import app.komikku.shared.backup.BackupProgressListener
import com.hippo.unifile.UniFile
import java.io.File

/**
 * KMK: desktop counterpart of the Android `BackupNotifier` (same member names and parameters) for the
 * borrowed `BackupRestorer`. Instead of notification builders, progress is a small [Progress] value whose
 * `show(id)` (Android: post the notification) forwards to [listener]; the desktop UI renders it.
 */
@Suppress("UNUSED_PARAMETER")
class BackupNotifier(
    private val context: Context,
    private val listener: BackupProgressListener = BackupProgressListener.None,
) {

    data class Progress(val content: String, val progress: Int, val max: Int)

    internal fun Progress.show(id: Int) {
        listener.onRestoreProgress(content, progress, max)
    }

    fun showBackupProgress() {
        listener.onBackupProgress()
    }

    fun showBackupError(error: String?) {
        listener.onBackupError(error)
    }

    fun showBackupComplete(file: UniFile) {
        listener.onBackupComplete(file.filePath ?: file.uri.toString())
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun showRestoreProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
        sync: Boolean = false,
    ): Progress = Progress(content, progress, maxAmount)

    fun showRestoreError(error: String?) {
        listener.onRestoreError(error)
    }

    fun showRestoreComplete(
        time: Long,
        errorCount: Int,
        path: String?,
        file: String?,
        sync: Boolean = false,
    ) {
        val log = if (errorCount > 0 && path != null && file != null) File(path, file) else null
        listener.onRestoreComplete(time, errorCount, log)
    }
}
