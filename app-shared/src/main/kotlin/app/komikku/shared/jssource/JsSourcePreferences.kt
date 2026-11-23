package app.komikku.shared.jssource

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * KMK: preferences of the Mangayomi JavaScript sources (docs/kmp-desktop/PROGRESS.md, "I4"). They live in the app's
 * main preference store, so the app-preference backup carries the repository list; installed sources are files of
 * [MangayomiSourceStore], not preferences.
 */
class JsSourcePreferences(private val preferenceStore: PreferenceStore) {

    /** Index URLs of the added Mangayomi repositories (`https` only). */
    fun repositories(): Preference<Set<String>> = preferenceStore.getStringSet(REPOSITORIES_KEY, emptySet())

    companion object {
        const val REPOSITORIES_KEY = "mangayomi_js_repos"
    }
}
