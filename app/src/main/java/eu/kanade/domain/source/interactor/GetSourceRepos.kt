package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSourceRepos(private val preferences: PreferencesHelper) {

    fun subscribe(): Flow<List<String>> {
        return preferences.extensionRepos().asFlow().map { it.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    }
}
