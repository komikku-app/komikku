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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest

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
    // New: onCommitOrder lets caller handle batched commit (recommended).
    // Default behaviour falls back to calling onChangeOrder for each changed item.
    onCommitOrder: (List<Pair<Category, Int>>) -> Unit = { changes ->
        // Default: sequentially call onChangeOrder.
        // For best perf, supply your own batch interactor here (recommended).
        changes.forEach { (cat, idx) -> onChangeOrder(cat, idx) }
    },
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
            onCommitOrder = onCommitOrder,
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
    onCommitOrder: (List<Pair<Category, Int>>) -> Unit,
) {
    val entries = remember(categories) { buildCategoryHierarchy(categories) }

    // Backing list used by LazyColumn (stateful, triggers recompositions on swap)
    val categoriesState = remember(entries) { entries.toMutableStateList() }

    // Flow to emit pending in-memory order; debounced collector will commit to model/DB.
    val pendingOrderFlow = remember { MutableStateFlow(categoriesState.map { it.category }) }

    // Track last committed indices to only commit changed items
    var lastCommittedIndices by remember { mutableStateOf(entries.mapIndexed { idx, e -> e.category.id to idx }.toMap()) }

    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        // Build a new list locally, perform all mutations there (single replace)
        val newList = categoriesState.toMutableList()

        // Helper to find subtree end index for parent moves
        fun findSubtreeEnd(startIndex: Int, list: MutableList<CategoryHierarchyEntry>): Int {
            val parentDepth = list[startIndex].depth
            var idx = startIndex + 1
            while (idx < list.size) {
                if (list[idx].depth <= parentDepth) break
                idx++
            }
            return idx
        }

        // If moving a parent category (depth == 0), move parent + contiguous children
        if (categoriesState[from.index].depth == 0) {
            val subtreeEnd = findSubtreeEnd(from.index, newList)
            val subtree = newList.subList(from.index, subtreeEnd).toList()
            // Remove subtree range
            newList.subList(from.index, subtreeEnd).clear()
            // Adjust target
            val adjustedTo = if (from.index < to.index) {
                // to.index refers to destination in the original list; after removal,
                // indices shift left by subtree.size
                to.index - subtree.size
            } else {
                to.index
            }.coerceIn(0, newList.size)
            // Insert subtree
            newList.addAll(adjustedTo, subtree)
        } else if (categoriesState[from.index].depth > 0) {
            // Moving a child, single-item move; perform on local list
            val item = newList.removeAt(from.index)
            val destIndex = to.index.coerceIn(0, newList.size)
            newList.add(destIndex, item)

            // Optionally detect parent change; update the Category in the entry if needed
            // We'll compute parent at dest position after swap when committing to model.
        } else {
            // Fallback single item move
            val item = newList.removeAt(from.index)
            val destIndex = to.index.coerceIn(0, newList.size)
            newList.add(destIndex, item)
        }

        // Replace the backing list in a single operation (reduces recompositions)
        categoriesState.clear()
        categoriesState.addAll(newList)

        // Publish the new in-memory category order for debounced commit
        pendingOrderFlow.value = newList.map { it.category }
    }

    // Debounced collector: commit changes after user stops moving items for a short period
    LaunchedEffect(pendingOrderFlow) {
        // Debounce duration: adjust to taste (ms)
        val debounceMs = 700L
        pendingOrderFlow
            .debounce(debounceMs)
            .collectLatest { newOrder ->
                // Compute changed items by comparing indices against lastCommittedIndices
                val changes = newOrder.mapIndexedNotNull { index, category ->
                    val lastIndex = lastCommittedIndices[category.id]
                    if (lastIndex == null || lastIndex != index) {
                        category to index
                    } else null
                }

                if (changes.isNotEmpty()) {
                    // Call commit callback (caller should optimize DB writes, ideally single transaction)
                    // Note: caller's implementation should execute DB work on IO dispatcher.
                    // The default onCommitOrder will call onChangeOrder sequentially.
                    onCommitOrder(changes)

                    // Update lastCommittedIndices to reflect committed order
                    lastCommittedIndices = newOrder.mapIndexed { idx, cat -> cat.id to idx }.toMap()
                }
            }
    }

    // Keep list in sync if underlying data changes (external DB changes)
    LaunchedEffect(entries) {
        categoriesState.clear()
        categoriesState.addAll(entries)
        // Also reset lastCommittedIndices to database-supplied order
        lastCommittedIndices = entries.mapIndexed { idx, e -> e.category.id to idx }.toMap()
        pendingOrderFlow.value = entries.map { it.category }
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
                    modifier = Modifier, // removed animateItem for baseline; re-add if you need
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

// Minimal intermediary type to make local helper type signatures readable
private interface AnyWithDepth {
    val depth: Int
}
