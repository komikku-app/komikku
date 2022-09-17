package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.core.preference.getAndSet
import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class ToggleSources(
    private val preferences: PreferencesHelper,
) {

    fun await(isEnable: Boolean, sources: List<Source>) {
        preferences.disabledSources().getAndSet { disabledSources ->
            if (isEnable) {
                disabledSources - sources.map { it.id.toString() }.toSet()
            } else {
                disabledSources + sources.map { it.id.toString() }
            }
        }
    }
}
