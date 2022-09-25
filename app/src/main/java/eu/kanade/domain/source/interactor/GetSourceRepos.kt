package eu.kanade.domain.source.interactor

import eu.kanade.domain.UnsortedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSourceRepos(private val preferences: UnsortedPreferences) {

    fun subscribe(): Flow<List<String>> {
        return preferences.extensionRepos().changes().map { it.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    }
}
