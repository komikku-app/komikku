package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class RenameCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String) = update(categoryId, name, null, false)

    private suspend fun update(
        categoryId: Long,
        name: String,
        parentId: Long?,
        parentIdChanged: Boolean,
    ) = withNonCancellableContext {
        try {
            if (parentIdChanged && createsCycle(categoryId, parentId)) {
                throw IllegalArgumentException("A category cannot be its own ancestor")
            }
            val update = CategoryUpdate(
                id = categoryId,
                name = name,
                parentId = parentId,
                parentIdChanged = parentIdChanged,
            )
            categoryRepository.updatePartial(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    private suspend fun createsCycle(categoryId: Long, parentId: Long?): Boolean {
        if (parentId == null) return false

        val categoriesById = categoryRepository.getAll().associateBy { it.id }
        val visited = mutableSetOf(categoryId)
        var currentId: Long? = parentId
        while (currentId != null) {
            if (!visited.add(currentId)) return true
            currentId = categoriesById[currentId]?.parentId
        }
        return false
    }

    suspend fun await(category: Category, name: String, parentId: Long? = category.parentId) = update(
        categoryId = category.id,
        name = name,
        parentId = parentId,
        parentIdChanged = parentId != category.parentId,
    )

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
