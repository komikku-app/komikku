package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.preference.minusAssign

class DeleteSourceCategory(private val preferences: SourcePreferences) {

    fun await(category: String) {
        preferences.sourcesTabSourcesInCategories().getAndSet { sourcesInCategories ->
            sourcesInCategories.filterNot { it.substringAfter("|") == category }.toSet()
        }
        preferences.sourcesTabCategories() -= category
    }
}
