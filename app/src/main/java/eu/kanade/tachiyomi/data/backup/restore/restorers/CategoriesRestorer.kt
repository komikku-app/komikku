package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            // KMK -->
            // Create every category without a parent first because backup IDs are not database IDs.
            // Once all IDs are known, restore the hierarchy in the same transaction.
            val categories = handler.await(inTransaction = true) {
                val restoredByBackupId = mutableMapOf<Long, Category>()
                val restored = backupCategories
                    .sortedBy { it.order }
                    .map { backupCategory ->
                        val existingCategory = dbCategoriesByName[backupCategory.name]
                        val category = existingCategory ?: run {
                            val order = nextOrder++
                            categoriesQueries.insert(
                                name = backupCategory.name,
                                order = order,
                                flags = backupCategory.flags,
                                parentId = null,
                                hidden = if (backupCategory.hidden) 1L else 0L,
                            )
                            backupCategory.toCategory(
                                id = categoriesQueries.selectLastInsertedRowId().executeAsOne(),
                                parentId = null,
                            ).copy(order = order)
                        }
                        restoredByBackupId[backupCategory.id] = category
                        RestoredCategory(
                            backup = backupCategory,
                            category = category,
                            wasCreated = existingCategory == null,
                        )
                    }

                val backupParents = restored.associate { restoredCategory ->
                    restoredCategory.backup.id to restoredCategory.backup.parentId
                        ?.takeUnless { it == BackupCategory.ROOT_PARENT_ID }
                }

                restored.map { restoredCategory ->
                    val backupCategory = restoredCategory.backup
                    val category = restoredCategory.category
                    val backupParentId = backupCategory.parentId
                        ?.takeUnless { it == BackupCategory.ROOT_PARENT_ID }
                        ?.takeUnless { parentId -> createsCycle(backupCategory.id, parentId, backupParents) }
                    val restoredParentId = when {
                        // A missing field means the backup predates subcategories. Preserve matched data.
                        backupCategory.parentId == null && !restoredCategory.wasCreated -> category.parentId
                        backupParentId == null -> null
                        else -> restoredByBackupId[backupParentId]?.id
                    }
                    if (category.parentId != restoredParentId) {
                        categoriesQueries.updateParent(
                            parentId = restoredParentId,
                            categoryId = category.id,
                        )
                    }
                    category.copy(parentId = restoredParentId)
                }
            }
            // KMK <--

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }

    private fun createsCycle(
        categoryId: Long,
        parentId: Long,
        parentsByCategoryId: Map<Long, Long?>,
    ): Boolean {
        val visited = mutableSetOf(categoryId)
        var currentId: Long? = parentId
        while (currentId != null) {
            if (!visited.add(currentId)) return true
            currentId = parentsByCategoryId[currentId]
        }
        return false
    }

    private data class RestoredCategory(
        val backup: BackupCategory,
        val category: Category,
        val wasCreated: Boolean,
    )
}
