package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.flow.Flow

class GetSourceCategories(
    private val preferences: PreferencesHelper,
) {

    fun subscribe(): Flow<Set<String>> {
        return preferences.sourcesTabCategories().asFlow()
    }
}
