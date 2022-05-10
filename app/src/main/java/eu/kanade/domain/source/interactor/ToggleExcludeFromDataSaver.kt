package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleExcludeFromDataSaver(
    private val preferences: PreferencesHelper,
) {

    fun await(source: Source) {
        val isExcluded = source.id.toString() in preferences.dataSaverExcludedSources().get()
        if (isExcluded) {
            preferences.dataSaverExcludedSources() -= source.id.toString()
        } else {
            preferences.dataSaverExcludedSources() += source.id.toString()
        }
    }
}
