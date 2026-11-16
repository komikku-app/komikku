package app.komikku.shared.backup

/**
 * KMK: what the borrowed `BackupRestorer` / `BackupCreator` report through Android's `BackupNotifier`
 * (notifications on Android). The desktop `BackupNotifier` counterpart forwards to this listener.
 */
interface BackupProgressListener {
    /** Restore progress: [content] is the item being restored (manga title, "Categories", …). */
    fun onRestoreProgress(content: String, progress: Int, max: Int) {}

    /** Restore finished in [timeMillis] with [errorCount] errors; [errorLog] lists them (when any). */
    fun onRestoreComplete(timeMillis: Long, errorCount: Int, errorLog: java.io.File?) {}

    fun onRestoreError(message: String?) {}

    fun onBackupProgress() {}

    fun onBackupComplete(path: String) {}

    fun onBackupError(message: String?) {}

    companion object {
        val None = object : BackupProgressListener {}
    }
}
