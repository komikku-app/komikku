package eu.kanade.tachiyomi.data.library

import android.content.Context

/**
 * KMK: desktop counterpart of the Android `LibraryUpdateJob` scheduling entry point for the borrowed
 * `PreferenceRestorer`: library updates are started by the desktop runner, not scheduled, so
 * [setupTask] does nothing.
 */
object LibraryUpdateJob {
    fun setupTask(context: Context, prefInterval: Int? = null) = Unit
}
