package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetShowLatest(
    private val preferences: PreferencesHelper
) {

    fun subscribe(mode: SourceController.Mode): Flow<Boolean> {
        return preferences.useNewSourceNavigation().asFlow()
            .map {
                mode == SourceController.Mode.CATALOGUE && !it
            }
    }
}
