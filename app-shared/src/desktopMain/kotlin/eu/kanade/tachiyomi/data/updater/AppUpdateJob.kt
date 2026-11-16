package eu.kanade.tachiyomi.data.updater

import android.content.Context

/**
 * KMK: desktop counterpart of the Android `AppUpdateJob` for the borrowed `PreferenceRestorer`: no app
 * updater on desktop yet, so [setupTask] does nothing.
 */
object AppUpdateJob {
    fun setupTask(context: Context) = Unit
}
