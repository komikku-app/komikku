package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetShowLatest(
    private val preferences: PreferencesHelper,
) {

    fun subscribe(mode: SourcesController.Mode): Flow<Boolean> {
        return preferences.useNewSourceNavigation().asFlow()
            .map {
                mode == SourcesController.Mode.CATALOGUE && !it
            }
    }
}
