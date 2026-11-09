package eu.kanade.domain.track.service

import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_password_${tracker.id}"),
        "",
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) = preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), "")

    // KMK -->
    /**
     * PKCE code verifier of the login in progress in the browser, until its code exchange. Stored rather than
     * kept in memory, so the login still completes when the app process is killed while the browser is open.
     *
     * App state rather than private: private preferences go into backups and sync when the user includes
     * private settings, and a sync restoring another device's verifier during a login would fail it.
     */
    fun trackPkceCodeVerifier(tracker: Tracker) = preferenceStore.getString(
        Preference.appStateKey("track_pkce_code_verifier_${tracker.id}"),
        "",
    )
    // KMK <--

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    // KMK -->
    val mangabakaScoreType = preferenceStore.getString("mangabaka_score_type", MangaBaka.STEP_1)
    // KMK <--

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun trackOnAddingToLibrary() = preferenceStore.getBoolean("track_on_adding_to_library", true)

    fun autoUpdateTrackOnMarkRead() = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    // SY -->
    fun resolveUsingSourceMetadata() = preferenceStore.getBoolean(
        "pref_resolve_using_source_metadata_key",
        true,
    )
    // SY <--

    // KMK -->
    fun autoSyncProgressFromTrackers() = preferenceStore.getBoolean("pref_auto_sync_progress_from_trackers_key", true)
    // KMK <--
}
