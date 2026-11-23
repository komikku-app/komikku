package app.komikku.desktop.scheduler

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * KMK: the desktop scheduler's own state, in the main preference store as app-state keys (not in backups, like
 * Android's WorkManager state).
 */
class DesktopSchedulerPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * When the scheduler last started a library update (epoch milliseconds, 0 = never): the due time of the scheduled
     * update. Android's periodic `LibraryUpdateJob` runs on its own period, so manual and partial updates (which set
     * `LibraryPreferences.lastUpdatedTimestamp()`, shown in the updates tab) do not move it.
     */
    fun lastScheduledLibraryUpdate() = preferenceStore.getLong(Preference.appStateKey("desktop_last_scheduled_library_update"), 0L)
}
