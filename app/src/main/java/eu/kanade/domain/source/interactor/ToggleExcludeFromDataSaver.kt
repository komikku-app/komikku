package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.getAndSet

class ToggleExcludeFromDataSaver(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source) {
        preferences.dataSaverExcludedSources().getAndSet {
            if (source.id.toString() in it) {
                it - source.id.toString()
            } else {
                it + source.id.toString()
            }
        }
    }
}
