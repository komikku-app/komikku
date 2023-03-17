package tachiyomi.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.UnsortedPreferences

class GetSourceRepos(private val preferences: UnsortedPreferences) {

    fun subscribe(): Flow<List<String>> {
        return preferences.extensionRepos().changes().map { it.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    }
}
