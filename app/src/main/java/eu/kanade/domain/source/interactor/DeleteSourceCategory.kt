package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign

class DeleteSourceCategory(private val preferences: PreferencesHelper) {

    fun await(category: String) {
        preferences.sourcesTabSourcesInCategories().set(
            preferences.sourcesTabSourcesInCategories().get()
                .filterNot { it.substringAfter("|") == category }
                .toSet(),
        )
        preferences.sourcesTabCategories() -= category
    }
}
