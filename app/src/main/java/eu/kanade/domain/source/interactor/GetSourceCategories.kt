package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSourceCategories(
    private val preferences: PreferencesHelper,
) {

    fun subscribe(): Flow<List<String>> {
        return preferences.sourcesTabCategories().asFlow().map { it.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    }
}
