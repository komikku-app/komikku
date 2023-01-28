package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.preference.minusAssign
import tachiyomi.core.preference.getAndSet

class DeleteSourceCategory(private val preferences: SourcePreferences) {

    fun await(category: String) {
        preferences.sourcesTabSourcesInCategories().getAndSet { sourcesInCategories ->
            sourcesInCategories.filterNot { it.substringAfter("|") == category }.toSet()
        }
        preferences.sourcesTabCategories() -= category
    }
}
