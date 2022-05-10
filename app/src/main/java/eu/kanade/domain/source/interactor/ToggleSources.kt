package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleSources(
    private val preferences: PreferencesHelper,
) {

    fun await(isEnable: Boolean, sources: List<Source>) {
        val newDisabledSources = if (isEnable) {
            preferences.disabledSources().get() - sources.map { it.id.toString() }
        } else {
            preferences.disabledSources().get() + sources.map { it.id.toString() }
        }
        preferences.disabledSources().set(newDisabledSources)
    }
}
