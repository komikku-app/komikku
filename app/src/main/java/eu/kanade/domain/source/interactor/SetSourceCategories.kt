package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class SetSourceCategories(
    private val preferences: PreferencesHelper,
) {

    fun await(source: Source, sourceCategories: List<String>) {
        val sourceIdString = source.id.toString()
        val currentSourceCategories = preferences.sourcesTabSourcesInCategories().get().filterNot {
            it.substringBefore('|') == sourceIdString
        }
        val newSourceCategories = currentSourceCategories + sourceCategories.map {
            "$sourceIdString|$it"
        }
        preferences.sourcesTabSourcesInCategories().set(newSourceCategories.toSet())
    }
}
