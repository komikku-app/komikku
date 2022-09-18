package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.getAndSet

class ToggleSources(
    private val preferences: SourcePreferences,
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
