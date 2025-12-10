package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onChangeParent: (Category, Long?) -> Unit,
    // KMK -->
    onClickHide: (Category) -> Unit,
    // KMK <--
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            categories = state.categories,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
            onChangeParent = onChangeParent,
            // KMK -->
            onClickHide = onClickHide,
            // KMK <--
        )
    }
}

@Composable
private fun CategoryContent(
    categories: ImmutableList<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onChangeParent: (Category, Long?) -> Unit,
    // KMK -->
    onClickHide: (Category) -> Unit,
    // KMK <--
) {
    val entries = remember(categories) { buildCategoryEntries(categories) }
    val categoriesState = remember { entries.toMutableStateList() }
    var needsRebuild by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val movedItem = categoriesState.removeAt(from.index)
        categoriesState.add(to.index, movedItem)

        // If moving a parent category, also move all its children
        if (movedItem.category.parentId == null && movedItem.depth == 0) {
            // This is a parent - find all children that should move with it
            val childrenToMove = mutableListOf<CategoryEntry>()

            // Find all children by checking depth and position
            // Children are any entries immediately following the parent with greater depth
            var checkIndex = to.index + 1
            while (checkIndex < categoriesState.size) {
                val entry = categoriesState[checkIndex]
                // Stop when we hit a non-child (same or lower depth)
                if (entry.depth <= movedItem.depth) {
                    break
                }
                childrenToMove.add(entry)
                checkIndex++
            }

            // Remove children from their current positions
            childrenToMove.forEach { categoriesState.remove(it) }

            // Re-insert children right after parent in the correct order
            var insertIndex = to.index + 1
            for (child in childrenToMove) {
                categoriesState.add(insertIndex, child)
                insertIndex++
            }
            
            // Mark for rebuild since parent position changed
            needsRebuild = true
        } else if (movedItem.category.parentId != null && movedItem.depth > 0) {
            // This is a child being dragged - check if it's being moved to a new parent
            val newParentId = findParentAtPosition(categoriesState, to.index)
            val oldParentId = movedItem.category.parentId
            
            // If parent changed, update it
            if (newParentId != oldParentId) {
                val updatedCategory = movedItem.category.copy(parentId = newParentId)
                movedItem.category = updatedCategory
                onChangeParent(updatedCategory, newParentId)
            }
        }

        // Recalculate order indices for all affected categories
        categoriesState.forEachIndexed { index, entry ->
            onChangeOrder(entry.category, index)
        }
    }

    LaunchedEffect(entries) {
        // Update the list when categories from database change
        categoriesState.clear()
        categoriesState.addAll(entries)
        needsRebuild = false
    }
    
    LaunchedEffect(needsRebuild) {
        if (needsRebuild) {
            // Small delay to allow database update, then rebuild
            kotlinx.coroutines.delay(50)
            val rebuiltEntries = buildCategoryEntries(categoriesState.map { it.category })
            categoriesState.clear()
            categoriesState.addAll(rebuiltEntries)
            needsRebuild = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues +
            topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = categoriesState,
            key = { entry -> entry.category.key },
        ) { entry ->
            ReorderableItem(reorderableState, entry.category.key) {
                val parentCategory = if (entry.category.parentId != null) {
                    categories.firstOrNull { it.id == entry.category.parentId }
                } else {
                    null
                }

                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    category = entry.category,
                    indentLevel = entry.depth,
                    isParent = entry.depth == 0,
                    parentCategory = parentCategory,
                    onRename = { onClickRename(entry.category) },
                    onDelete = { onClickDelete(entry.category) },
                    // KMK -->
                    onHide = { onClickHide(entry.category) },
                    // KMK <--
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"

private data class CategoryEntry(
    var category: Category,
    val depth: Int,
)

private fun findParentAtPosition(entries: List<CategoryEntry>, position: Int): Long? {
    if (position <= 0) return null
    
    // Look backward to find the nearest parent (depth == 0)
    for (i in position - 1 downTo 0) {
        if (entries[i].depth == 0) {
            return entries[i].category.id
        }
    }
    return null
}

private fun buildCategoryEntries(categories: List<Category>): List<CategoryEntry> {
    if (categories.isEmpty()) return emptyList()

    val byParent = categories.groupBy { it.parentId }
    val visited = mutableSetOf<Long>()
    val result = mutableListOf<CategoryEntry>()

    fun traverse(parentId: Long?, depth: Int) {
        val children = byParent[parentId].orEmpty()
            .sortedBy { it.order }
        for (child in children) {
            if (visited.add(child.id)) {
                result += CategoryEntry(child, depth)
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
            result += CategoryEntry(orphan, 0)
            traverse(orphan.id, 1)
        }

    return result
}
