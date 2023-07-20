package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.plusAssign

class CreateSourceCategory(private val preferences: SourcePreferences) {

    fun await(category: String): Result {
        if (category.contains("|")) {
            return Result.InvalidName
        }

        // Create category.
        preferences.sourcesTabCategories() += category

        return Result.Success
    }

    sealed class Result {
        object InvalidName : Result()
        object Success : Result()
    }
}
