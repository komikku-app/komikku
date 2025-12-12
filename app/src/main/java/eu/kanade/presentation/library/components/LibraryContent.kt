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
import androidx.compose.material3.HorizontalDivider
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
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    activeCategoryIndex: Int,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    showParentFilters: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
) {
    // Derive parent categories and child mapping
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null && !it.isSystemCategory }.sortedBy { it.order }
    }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.sortedBy { it.order } }
    }

    // Track selected subcategory (null = show all/parent items)
    var activeSubcategoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        // Determine which categories to show in tabs based on showParentFilters
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty()) {
            parentCategories
        } else {
            categories
        }

        // Calculate initial page based on activeCategoryIndex
        val initialPage = when {
            tabCategories.isEmpty() -> 0
            activeCategoryIndex in tabCategories.indices -> activeCategoryIndex
            currentPage in tabCategories.indices -> currentPage
            else -> 0
        }

        val pagerState = rememberPagerState(initialPage = initialPage) { tabCategories.size }
        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        // Show tabs if needed
        if (showPageTabs && tabCategories.isNotEmpty() && (tabCategories.size > 1 || !tabCategories.first().isSystemCategory)) {
            LaunchedEffect(tabCategories, activeCategoryIndex) {
                val targetPage = when {
                    tabCategories.isEmpty() -> 0
                    activeCategoryIndex != pagerState.currentPage && activeCategoryIndex in tabCategories.indices -> activeCategoryIndex
                    pagerState.currentPage >= tabCategories.size -> tabCategories.size - 1
                    else -> pagerState.currentPage
                }
                if (targetPage != pagerState.currentPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }

            LibraryTabs(
                categories = tabCategories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
        }

        // Show subcategory filter chips if parent filters are enabled
        if (showParentFilters && parentCategories.isNotEmpty()) {
            val activeParent = parentCategories.getOrNull(pagerState.currentPage)
            val subcategoriesForActiveParent = activeParent?.let { childrenByParent[it.id] }.orEmpty()

            // Reset activeSubcategoryId if no subcategories for current parent
            LaunchedEffect(subcategoriesForActiveParent) {
                if (subcategoriesForActiveParent.isEmpty()) {
                    activeSubcategoryId = null
                }
            }

            if (subcategoriesForActiveParent.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // "All" chip to show parent + all subcategory items
                    item {
                        FilterChip(
                            selected = activeSubcategoryId == null,
                            onClick = { activeSubcategoryId = null },
                            label = { Text(text = "All") },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }

                    // Subcategory chips
                    items(subcategoriesForActiveParent) { sub ->
                        val selected = activeSubcategoryId == sub.id
                        FilterChip(
                            selected = selected,
                            onClick = { activeSubcategoryId = if (selected) null else sub.id },
                            label = { Text(text = sub.visualName) },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }

            HorizontalDivider()
        }

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
            // Wrapper function to handle item fetching based on parent filter state
            val wrappedGetItemsForCategory: (Category) -> List<LibraryItem> = { pageCategory ->
                if (showParentFilters) {
                    // Parent filters enabled: respect subcategory selection
                    val selectedSub = activeSubcategoryId?.let { id ->
                        categories.firstOrNull { it.id == id }
                    }

                    if (selectedSub != null && selectedSub.parentId == pageCategory.id) {
                        // Show only the selected subcategory's items
                        getItemsForCategory(selectedSub)
                    } else if (activeSubcategoryId == null) {
                        // "All" selected: merge parent + all subcategory items (deduped)
                        val parentItems = getItemsForCategory(pageCategory)
                        val children = childrenByParent[pageCategory.id].orEmpty()
                        val childItems = children.flatMap { child -> getItemsForCategory(child) }

                        val seen = mutableSetOf<Long>()
                        val merged = mutableListOf<LibraryItem>()
                        (parentItems + childItems).forEach { item ->
                            val mangaId = item.libraryManga.manga.id
                            if (seen.add(mangaId)) merged.add(item)
                        }
                        merged
                    } else {
                        // Subcategory selected but doesn't belong to current parent
                        getItemsForCategory(pageCategory)
                    }
                } else {
                    // Parent filters disabled: always merge parent + all subcategories
                    val parentItems = getItemsForCategory(pageCategory)
                    val children = childrenByParent[pageCategory.id].orEmpty()
                    val childItems = children.flatMap { child -> getItemsForCategory(child) }

                    val seen = mutableSetOf<Long>()
                    val merged = mutableListOf<LibraryItem>()
                    (parentItems + childItems).forEach { item ->
                        val mangaId = item.libraryManga.manga.id
                        if (seen.add(mangaId)) merged.add(item)
                    }
                    merged
                }
            }

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
                getItemsForCategory = wrappedGetItemsForCategory,
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

        LaunchedEffect(pagerState.currentPage) {
            // Reset subcategory selection when parent page changes
            if (showParentFilters) {
                activeSubcategoryId = null
            }
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
