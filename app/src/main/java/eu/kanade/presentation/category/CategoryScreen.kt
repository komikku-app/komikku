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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
    expanded: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    // KMK <--
    onCommitOrder: (List<Pair<Category, Int>>) -> Unit = { changes ->
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
            expanded = expanded,
            onToggleExpand = onToggleExpand,
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
    expanded: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    // KMK <--
    onCommitOrder: (List<Pair<Category, Int>>) -> Unit,
) {
    // Filter entries based on expand/collapse state
    val entries = remember(categories, expanded) {
        buildCategoryHierarchy(categories).filter { entry ->
            // Always show parent categories (depth == 0)
            if (entry.depth == 0) return@filter true

            // Show children only if their parent is expanded
            val parentId = entry.category.parentId
            parentId != null && expanded.contains(parentId)
        }
    }

    val categoriesState = remember(entries) { entries.toMutableStateList() }
    val pendingOrderFlow = remember { MutableStateFlow(categoriesState.map { it.category }) }
    var lastCommittedIndices by remember { mutableStateOf(entries.mapIndexed { idx, e -> e.category.id to idx }.toMap()) }

    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val newList = categoriesState.toMutableList()

        fun findSubtreeEnd(startIndex: Int, list: MutableList<CategoryHierarchyEntry>): Int {
            val parentDepth = list[startIndex].depth
            var idx = startIndex + 1
            while (idx < list.size) {
                if (list[idx].depth <= parentDepth) break
                idx++
            }
            return idx
        }

        if (categoriesState[from.index].depth == 0) {
            val subtreeEnd = findSubtreeEnd(from.index, newList)
            val subtree = newList.subList(from.index, subtreeEnd).toList()
            newList.subList(from.index, subtreeEnd).clear()
            val adjustedTo = if (from.index < to.index) {
                to.index - subtree.size
            } else {
                to.index
            }.coerceIn(0, newList.size)
            newList.addAll(adjustedTo, subtree)
        } else if (categoriesState[from.index].depth > 0) {
            val item = newList.removeAt(from.index)
            val destIndex = to.index.coerceIn(0, newList.size)
            newList.add(destIndex, item)
        } else {
            val item = newList.removeAt(from.index)
            val destIndex = to.index.coerceIn(0, newList.size)
            newList.add(destIndex, item)
        }

        categoriesState.clear()
        categoriesState.addAll(newList)
        pendingOrderFlow.value = newList.map { it.category }
    }

    LaunchedEffect(pendingOrderFlow) {
        val debounceMs = 700L
        pendingOrderFlow
            .debounce(debounceMs)
            .collectLatest { newOrder ->
                val changes = newOrder.mapIndexedNotNull { index, category ->
                    val lastIndex = lastCommittedIndices[category.id]
                    if (lastIndex == null || lastIndex != index) {
                        category to index
                    } else {
                        null
                    }
                }

                if (changes.isNotEmpty()) {
                    onCommitOrder(changes)
                    lastCommittedIndices = newOrder.mapIndexed { idx, cat -> cat.id to idx }.toMap()
                }
            }
    }

    LaunchedEffect(entries) {
        categoriesState.clear()
        categoriesState.addAll(entries)
        lastCommittedIndices = entries.mapIndexed { idx, e -> e.category.id to idx }.toMap()
        pendingOrderFlow.value = entries.map { it.category }
    }

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

                // Check if this category has children
                val hasChildren = categories.any { it.parentId == entry.category.id }
                val isExpanded = expanded.contains(entry.category.id)

                CategoryListItem(
                    modifier = Modifier,
                    category = entry.category,
                    indentLevel = entry.depth,
                    isParent = entry.depth == 0,
                    parentCategory = parentCategory,
                    onRename = { onClickRename(entry.category) },
                    onDelete = { onClickDelete(entry.category) },
                    // KMK -->
                    onHide = { onClickHide(entry.category) },
                    hasChildren = hasChildren,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggleExpand(entry.category.id) },
                    // KMK <--
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"

private interface AnyWithDepth {
    val depth: Int
}
