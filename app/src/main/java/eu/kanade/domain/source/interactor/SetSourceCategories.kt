package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.getAndSet

class SetSourceCategories(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source, sourceCategories: List<String>) {
        val sourceIdString = source.id.toString()
        preferences.sourcesTabSourcesInCategories().getAndSet { sourcesInCategories ->
            val currentSourceCategories = sourcesInCategories.filterNot {
                it.substringBefore('|') == sourceIdString
            }
            val newSourceCategories = currentSourceCategories + sourceCategories.map {
                "$sourceIdString|$it"
            }
            newSourceCategories.toSet()
        }
    }
}
