package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.repository.CategoryRepository

class InsertCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(name: String, order: Long): Result {
        return try {
            // SY -->
            Result.Success(categoryRepository.insert(name, order))
            // SY <--
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed class Result {
        // SY -->
        data class Success(val id: Long) : Result()

        // Sy <--
        data class Error(val error: Exception) : Result()
    }
}
