package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.category.buildCategoryDescendants
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    // KMK -->
    activeCategoryId: Long?,
    // KMK <--
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    // KMK -->
    showParentFilters: Boolean,
    onChangeCurrentCategory: (Category) -> Unit,
    onVisibleItemsChanged: (Long?, List<Long>) -> Unit,
    // KMK <--
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    itemIdsByCategory: Map<Category, List<Long>>,
    aggregatedItemIdsByCategory: Map<Category, List<Long>>,
    itemsById: Map<Long, LibraryItem>,
) {
    // KMK -->
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val hierarchy = remember(categories) { buildCategoryHierarchy(categories) }
    val parentCategories = remember(hierarchy) { hierarchy.filter { it.depth == 0 }.map { it.category } }
    val descendantsByCategory = remember(categories) { buildCategoryDescendants(categories) }
    val tabCategories = if (showParentFilters && parentCategories.isNotEmpty()) {
        parentCategories
    } else {
        categories
    }
    val activeTabCategoryId = remember(activeCategoryId, categoryById, showParentFilters) {
        if (showParentFilters) {
            activeCategoryId?.let { findTopLevelCategoryId(it, categoryById) }
        } else {
            activeCategoryId
        }
    }
    val activeTabIndex = tabCategories
        .indexOfFirst { it.id == activeTabCategoryId }
        .takeIf { it >= 0 }
        ?: 0

    var excludeSubcategoriesParentIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    // KMK <--

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val pagerState = rememberPagerState(initialPage = activeTabIndex) { tabCategories.size }
        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (
            showPageTabs &&
            tabCategories.isNotEmpty() &&
            (tabCategories.size > 1 || !tabCategories.first().isSystemCategory)
        ) {
            LaunchedEffect(tabCategories, activeTabIndex) {
                val targetPage = activeTabIndex.coerceAtMost(tabCategories.lastIndex)
                if (targetPage != pagerState.currentPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
            LibraryTabs(
                categories = tabCategories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = { page ->
                    scope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
            )
        }

        // KMK -->
        val activeParent = tabCategories.getOrNull(pagerState.currentPage)
        var activeSubcategoryId by rememberSaveable(activeParent?.id) { mutableStateOf<Long?>(null) }
        val subcategories = if (showParentFilters) {
            activeParent?.let { descendantsByCategory[it.id] }.orEmpty()
        } else {
            emptyList()
        }
        val isExcludingSubcategories = activeParent?.id
            ?.let { it in excludeSubcategoriesParentIds }
            ?: false

        if (subcategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = activeSubcategoryId == null && !isExcludingSubcategories,
                        onClick = {
                            activeSubcategoryId = null
                            activeParent?.id?.let { parentId ->
                                excludeSubcategoriesParentIds -= parentId
                            }
                        },
                        label = { Text(text = stringResource(MR.strings.all)) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                item(key = "parent-only") {
                    FilterChip(
                        selected = activeSubcategoryId == null && isExcludingSubcategories,
                        onClick = {
                            activeSubcategoryId = null
                            activeParent?.id?.let { parentId ->
                                excludeSubcategoriesParentIds += parentId
                            }
                        },
                        label = { Text(text = stringResource(KMR.strings.library_filter_parent_only)) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                items(
                    items = subcategories,
                    key = { it.id },
                ) { subcategory ->
                    val selected = activeSubcategoryId == subcategory.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeSubcategoryId = subcategory.id.takeUnless { selected }
                            activeParent?.id?.let { parentId ->
                                excludeSubcategoriesParentIds -= parentId
                            }
                        },
                        label = { Text(text = subcategory.visualName) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
        // KMK <--

        // KMK --> Cache merged pages until library data or the selected hierarchy scope changes.
        val visibleItemsCache = remember(
            itemIdsByCategory,
            aggregatedItemIdsByCategory,
            itemsById,
            categories,
            showParentFilters,
            activeSubcategoryId,
            excludeSubcategoriesParentIds,
        ) {
            mutableMapOf<Long, List<LibraryItem>>()
        }
        val getVisibleItemsForCategory: (Category) -> List<LibraryItem> = { pageCategory ->
            visibleItemsCache.getOrPut(pageCategory.id) {
                if (!showParentFilters) {
                    getCategoryItems(pageCategory, itemIdsByCategory, itemsById)
                } else {
                    val selectedSubcategory = activeSubcategoryId?.let(categoryById::get)
                    when {
                        selectedSubcategory != null &&
                            selectedSubcategory in descendantsByCategory[pageCategory.id].orEmpty() -> {
                            getCategoryItems(
                                category = selectedSubcategory,
                                itemIdsByCategory = aggregatedItemIdsByCategory,
                                itemsById = itemsById,
                            )
                        }
                        pageCategory.id in excludeSubcategoriesParentIds -> {
                            getCategoryItems(pageCategory, itemIdsByCategory, itemsById)
                        }
                        else -> {
                            getCategoryItems(
                                category = pageCategory,
                                itemIdsByCategory = aggregatedItemIdsByCategory,
                                itemsById = itemsById,
                            )
                        }
                    }
                }
            }
        }
        val currentVisibleItems = activeParent?.let(getVisibleItemsForCategory).orEmpty()
        val currentVisibleItemIds = remember(currentVisibleItems) {
            currentVisibleItems.map { it.libraryManga.manga.id }
        }
        LaunchedEffect(activeParent?.id, currentVisibleItemIds) {
            onVisibleItemsChanged(activeParent?.id, currentVisibleItemIds)
        }
        // KMK <--

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            onRefresh = {
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> tabCategories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getItemsForCategory = getVisibleItemsForCategory,
                onClickManga = { category, manga ->
                    if (selection.isNotEmpty()) {
                        onToggleSelection(category, manga)
                    } else {
                        onClickManga(manga.manga.id)
                    }
                },
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }

        LaunchedEffect(pagerState.currentPage, tabCategories) {
            tabCategories.getOrNull(pagerState.currentPage)?.let(onChangeCurrentCategory)
        }
    }
}

// KMK -->
private fun findTopLevelCategoryId(
    categoryId: Long,
    categoryById: Map<Long, Category>,
): Long {
    var category = categoryById[categoryId] ?: return categoryId
    val visited = mutableSetOf(category.id)

    while (category.parentId != null) {
        val parent = categoryById[category.parentId] ?: break
        if (!visited.add(parent.id)) break
        category = parent
    }
    return category.id
}

private fun getCategoryItems(
    category: Category,
    itemIdsByCategory: Map<Category, List<Long>>,
    itemsById: Map<Long, LibraryItem>,
): List<LibraryItem> {
    return itemIdsByCategory[category].orEmpty().mapNotNull(itemsById::get)
}
// KMK <--
