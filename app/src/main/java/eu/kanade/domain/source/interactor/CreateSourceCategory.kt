package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.plusAssign

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
        data object InvalidName : Result()
        data object Success : Result()
    }
}
