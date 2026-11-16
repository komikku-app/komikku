package eu.kanade.tachiyomi.data.backup.create

import android.content.Context

/**
 * KMK: desktop counterpart of the Android `BackupCreateJob` (WorkManager) for the borrowed
 * `PreferenceRestorer`: automatic backups are not scheduled on desktop yet, so [setupTask] does nothing.
 */
object BackupCreateJob {
    fun setupTask(context: Context, prefInterval: Int? = null) = Unit
}
