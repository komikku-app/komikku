package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class RenameSourceCategory(
    private val preferences: PreferencesHelper,
    private val createSourceCategory: CreateSourceCategory,
) {

    fun await(categoryOld: String, categoryNew: String): CreateSourceCategory.Result {
        when (val result = createSourceCategory.await(categoryNew)) {
            CreateSourceCategory.Result.CategoryExists -> return result
            CreateSourceCategory.Result.InvalidName -> return result
            CreateSourceCategory.Result.Success -> {}
        }

        preferences.sourcesTabSourcesInCategories().set(
            preferences.sourcesTabSourcesInCategories().get()
                .map {
                    val index = it.indexOf('|')
                    if (index != -1 && it.substring(index + 1) == categoryOld) {
                        it.substring(0, index + 1) + categoryNew
                    } else it
                }
                .toSet(),
        )
        preferences.sourcesTabCategories().set(
            preferences.sourcesTabCategories().get()
                .minus(categoryOld)
                .plus(categoryNew),
        )

        return CreateSourceCategory.Result.Success
    }
}
