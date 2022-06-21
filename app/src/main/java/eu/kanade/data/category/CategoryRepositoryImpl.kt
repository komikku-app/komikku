package eu.kanade.data.category

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.listOfLongsAdapter
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.domain.category.repository.DuplicateNameException
import kotlinx.coroutines.flow.Flow

class CategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryRepository {

    override fun getAll(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(categoryMapper) }
    }

    @Throws(DuplicateNameException::class)
    override suspend fun insert(name: String, order: Long) {
        if (checkDuplicateName(name)) throw DuplicateNameException(name)
        handler.await {
            categoriesQueries.insert(
                name = name,
                order = order,
                flags = 0L,
                // SY -->
                mangaOrder = emptyList(),
                // SY <--
            )
        }
    }

    @Throws(DuplicateNameException::class)
    override suspend fun update(payload: CategoryUpdate) {
        if (payload.name != null && checkDuplicateName(payload.name)) throw DuplicateNameException(payload.name)
        handler.await {
            categoriesQueries.update(
                name = payload.name,
                order = payload.order,
                flags = payload.flags,
                categoryId = payload.id,
                // SY -->
                mangaOrder = payload.mangaOrder?.let(listOfLongsAdapter::encode),
                // SY <--
            )
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }

    override suspend fun getCategoriesForManga(mangaId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByMangaId(mangaId, categoryMapper)
        }
    }

    override suspend fun checkDuplicateName(name: String): Boolean {
        return handler
            .awaitList { categoriesQueries.getCategories() }
            .any { it.name == name }
    }
}
