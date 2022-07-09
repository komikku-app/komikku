package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSortTag(private val preferences: PreferencesHelper) {

    fun subscribe(): Flow<List<String>> {
        return preferences.sortTagsForLibrary().asFlow()
            .map(::mapSortTags)
    }

    fun await() = getSortTags(preferences).let(::mapSortTags)

    companion object {
        fun getSortTags(preferences: PreferencesHelper) = preferences.sortTagsForLibrary().get()

        fun mapSortTags(tags: Set<String>) = tags.mapNotNull {
            val index = it.indexOf('|')
            if (index != -1) {
                (it.substring(0, index).toIntOrNull() ?: return@mapNotNull null) to it.substring(index + 1)
            } else null
        }
            .sortedBy { it.first }.map { it.second }
    }
}
