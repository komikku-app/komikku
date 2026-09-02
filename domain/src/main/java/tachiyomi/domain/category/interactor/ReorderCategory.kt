package tachiyomi.domain.category.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(categoriesInOrder: List<Category>) = withNonCancellableContext {
        mutex.withLock {
            val currentCategories = categoryRepository.getAll()
                .filterNot(Category::isSystemCategory)
            val currentCategoriesById = currentCategories.associateBy { it.id }
            val requestedIds = categoriesInOrder.mapTo(mutableSetOf()) { it.id }
            val reorderedCategories = categoriesInOrder
                .mapNotNull { currentCategoriesById[it.id] }
                .distinctBy { it.id } +
                currentCategories.filterNot { it.id in requestedIds }

            try {
                val updates = reorderedCategories.mapIndexedNotNull { index, category ->
                    val order = index.toLong()
                    CategoryUpdate(id = category.id, order = order)
                        .takeIf { category.order != order }
                }

                if (updates.isEmpty()) return@withNonCancellableContext Result.Unchanged
                categoryRepository.updatePartial(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
