package eu.kanade.presentation.category

import tachiyomi.domain.category.model.Category

/**
 * Represents a category with its hierarchical depth for display purposes.
 */
data class CategoryHierarchyEntry(
    val category: Category,
    val depth: Int,
)

/**
 * Builds a hierarchical list of categories with their depth levels.
 * Categories are ordered as parent followed by children, recursively.
 * Orphaned categories (those with invalid parent references) are included at the end.
 *
 * @param categories The list of categories to organize
 * @return A list of CategoryHierarchyEntry with proper depth values
 */
fun buildCategoryHierarchy(categories: List<Category>): List<CategoryHierarchyEntry> {
    if (categories.isEmpty()) return emptyList()

    val byParent = categories.groupBy { it.parentId }
    val visited = mutableSetOf<Long>()
    val result = mutableListOf<CategoryHierarchyEntry>()

    fun traverse(parentId: Long?, depth: Int) {
        val children = byParent[parentId].orEmpty()
            .sortedBy { it.order }
        for (child in children) {
            if (visited.add(child.id)) {
                result += CategoryHierarchyEntry(child, depth)
                traverse(child.id, depth + 1)
            }
        }
    }

    // First pass: traverse all categories with parentId == null (top-level parents)
    traverse(null, 0)

    // Second pass: include any orphaned categories that did not get visited
    categories.filter { it.id !in visited }
        .sortedBy { it.order }
        .forEach { orphan ->
            visited.add(orphan.id)
            result += CategoryHierarchyEntry(orphan, 0)
            traverse(orphan.id, 1)
        }

    return result
}

/**
 * Finds the parent category at a given position in a hierarchical list.
 * Looks backward from the position to find the nearest category with depth 0.
 *
 * @param entries The hierarchical category list
 * @param position The position to check
 * @return The parent category ID, or null if no parent found
 */
fun findParentAtPosition(entries: List<CategoryHierarchyEntry>, position: Int): Long? {
    if (position <= 0) return null

    // Look backward to find the nearest parent (depth == 0)
    for (i in position - 1 downTo 0) {
        if (entries[i].depth == 0) {
            return entries[i].category.id
        }
    }
    return null
}
