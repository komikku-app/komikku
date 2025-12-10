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
    val entries = remember(categories) { buildCategoryHierarchy(categories) }
    val categoriesState = remember(entries) { entries.toMutableStateList() }

    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        // If moving a parent category, collect its entire subtree first
        if (categoriesState[from.index].category.parentId == null && categoriesState[from.index].depth == 0) {
            // Collect the parent and all its contiguous children from the original position
            val movedParent = categoriesState[from.index]
            val subtreeToMove = mutableListOf(movedParent)
            
            // Scan forward from original position to collect all children
            var checkIndex = from.index + 1
            while (checkIndex < categoriesState.size) {
                val entry = categoriesState[checkIndex]
                // Stop when we hit a non-child (same or lower depth as parent)
                if (entry.depth <= movedParent.depth) {
                    break
                }
                subtreeToMove.add(entry)
                checkIndex++
            }
            
            // Remove the entire subtree from current position
            subtreeToMove.forEach { categoriesState.remove(it) }
            
            // Calculate adjusted target index (account for removed items before target)
            val adjustedTo = if (from.index < to.index) {
                to.index - subtreeToMove.size
            } else {
                to.index
            }
            
            // Insert parent and all its children at the target position
            subtreeToMove.forEachIndexed { offset, entry ->
                categoriesState.add(adjustedTo + offset, entry)
            }
        } else if (categoriesState[from.index].category.parentId != null && categoriesState[from.index].depth > 0) {
            // Moving a child - standard single item move
            val movedItem = categoriesState.removeAt(from.index)
            categoriesState.add(to.index, movedItem)
            
            // Check if it's being moved to a new parent
            val newParentId = findParentAtPosition(categoriesState, to.index)
            val oldParentId = movedItem.category.parentId
            
            // If parent changed, update it
            if (newParentId != oldParentId) {
                val updatedCategory = movedItem.category.copy(parentId = newParentId)
                categoriesState[to.index] = movedItem.copy(category = updatedCategory)
                onChangeParent(updatedCategory, newParentId)
            }
        } else {
            // Standard single item move for non-parent items
            val movedItem = categoriesState.removeAt(from.index)
            categoriesState.add(to.index, movedItem)
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
    }

    // Pre-compute categories map for efficient parent lookups
    val categoriesById = remember(categories) { categories.associateBy { it.id } }

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
                val parentCategory = entry.category.parentId?.let { categoriesById[it] }

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
