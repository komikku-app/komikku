package eu.kanade.domain.manga.interactor

import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.tachiyomi.util.preference.minusAssign

class DeleteSortTag(
    private val preferences: LibraryPreferences,
    private val getSortTag: GetSortTag,
) {

    fun await(tag: String) {
        getSortTag.await().withIndex().find { it.value == tag }?.let {
            preferences.sortTagsForLibrary() -= CreateSortTag.encodeTag(it.index, it.value)
        }
    }
}
