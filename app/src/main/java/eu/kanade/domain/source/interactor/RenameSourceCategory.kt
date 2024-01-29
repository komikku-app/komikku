package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class RenameSourceCategory(
    private val preferences: SourcePreferences,
    private val createSourceCategory: CreateSourceCategory,
) {

    fun await(categoryOld: String, categoryNew: String): CreateSourceCategory.Result {
        when (val result = createSourceCategory.await(categoryNew)) {
            CreateSourceCategory.Result.InvalidName -> return result
            CreateSourceCategory.Result.Success -> {}
        }

        preferences.sourcesTabSourcesInCategories().getAndSet { sourcesInCategories ->
            sourcesInCategories.map {
                val index = it.indexOf('|')
                if (index != -1 && it.substring(index + 1) == categoryOld) {
                    it.substring(0, index + 1) + categoryNew
                } else {
                    it
                }
            }.toSet()
        }
        preferences.sourcesTabCategories().getAndSet {
            it.minus(categoryOld).plus(categoryNew)
        }

        return CreateSourceCategory.Result.Success
    }
}
