package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.preference.plusAssign

class CreateSourceCategory(private val preferences: SourcePreferences) {

    fun await(category: String): Result {
        // Do not allow duplicate categories.
        if (categoryExists(category)) {
            return Result.CategoryExists
        }

        if (category.contains("|")) {
            return Result.InvalidName
        }

        // Create category.
        preferences.sourcesTabCategories() += category

        return Result.Success
    }

    sealed class Result {
        object CategoryExists : Result()
        object InvalidName : Result()
        object Success : Result()
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean {
        return preferences.sourcesTabCategories().get().any { it.equals(name, true) }
    }
}
