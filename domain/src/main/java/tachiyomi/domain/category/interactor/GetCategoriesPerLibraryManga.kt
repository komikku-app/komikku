package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.repository.CategoryRepository

class GetCategoriesPerLibraryManga(
    private val categoryRepository: CategoryRepository,
) {

    fun subscribe(): Flow<Map<Long, Set<Long>>> {
        return categoryRepository.getCategoriesPerLibraryMangaAsFlow()
    }
}
