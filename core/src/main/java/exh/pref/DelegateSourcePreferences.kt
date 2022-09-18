package exh.pref

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore

class DelegateSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun delegateSources(): Preference<Boolean> {
        return preferenceStore.getBoolean("eh_delegate_sources", true)
    }

    fun useJapaneseTitle(): Preference<Boolean> {
        return preferenceStore.getBoolean("use_jp_title", false)
    }
}
