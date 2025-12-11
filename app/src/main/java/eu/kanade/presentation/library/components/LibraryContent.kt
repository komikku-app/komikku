package eu.kanade.presentation.library.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    // KMK -->
    activeCategoryIndex: Int = 0,
    // KMK <--
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
    // Derive parent categories (top-level) and a map of parentId -> children
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null && !it.isSystemCategory }.sortedBy { it.order }
    }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.sortedBy { it.order } }
    }

    // Track the currently selected subcategory (chip). Null means "no subcategory filter" (show parent items).
    var activeSubcategoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        // Keep the original Column content but make the pager pages correspond to parentCategories
        Column(modifier = Modifier.zIndex(0f)) {
            // Pager for parent categories
            val pagerState = rememberPagerState(currentPage) { parentCategories.size.coerceAtLeast(1) }
            var chipsVisible by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

            // accumulator to avoid toggling for tiny jitter
            var scrollAccum by remember { mutableStateOf(0f) }
            val threshold = 24f // adjust sensitivity (px)
            val chipsOffsetDp by animateDpAsState(
                targetValue = if (chipsVisible) 0.dp else (-56).dp, // -height of chips row
                animationSpec = tween(durationMillis = 200),
            )
            val chipsAlpha by animateFloatAsState(if (chipsVisible) 1f else 0f, tween(200))

            // Show subcategory chips row (children of the active parent) under the tabs when enabled
            if (showParentFilters && parentCategories.isNotEmpty()) {
                // Display tabs of parent categories
                if (showPageTabs) {
                    LaunchedEffect(parentCategories) {
                        val targetPage = when {
                            parentCategories.isEmpty() -> 0
                            currentPage != pagerState.currentPage -> currentPage.coerceAtMost(parentCategories.size - 1)
                            activeCategoryIndex != pagerState.currentPage -> activeCategoryIndex.coerceAtMost(categories.size - 1)
                            pagerState.currentPage >= parentCategories.size -> parentCategories.size - 1
                            else -> pagerState.currentPage
                        }
                        if (targetPage != pagerState.currentPage) {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                    LibraryTabs(
                        categories = parentCategories,
                        pagerState = pagerState,
                        getItemCountForCategory = getItemCountForCategory,
                        onTabItemClick = {
                            scope.launch { pagerState.animateScrollToPage(it) }
                        },
                    )
                }

                // Subcategory chips for the currently active parent page
                val activeParent = parentCategories.getOrNull(pagerState.currentPage)
                val subcategoriesForActiveParent = activeParent?.let { childrenByParent[it.id] }.orEmpty()

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
                        // "All" chip to clear subcategory filter (shown because there are subcategories)
                        item {
                            FilterChip(
                                selected = activeSubcategoryId == null,
                                onClick = { activeSubcategoryId = null },
                                label = { Text(text = "All") },
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }

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
            }else {
                // If parent filters are disabled, fall back to showing the tabs for parentCategories normally
                if (showPageTabs && parentCategories.isNotEmpty()) {
                    LaunchedEffect(parentCategories) {
                        val targetPage = when {
                            parentCategories.isEmpty() -> 0
                            currentPage != pagerState.currentPage -> currentPage.coerceAtMost(parentCategories.size - 1)
                            pagerState.currentPage >= parentCategories.size -> parentCategories.size - 1
                            else -> pagerState.currentPage
                        }
                        if (targetPage != pagerState.currentPage) {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                    LibraryTabs(
                        categories = parentCategories,
                        pagerState = pagerState,
                        getItemCountForCategory = getItemCountForCategory,
                        onTabItemClick = {
                            scope.launch { pagerState.animateScrollToPage(it) }
                        },
                    )
                    HorizontalDivider()
                }
            }

            PullRefresh(
                refreshing = isRefreshing,
                enabled = selection.isEmpty(),
                onRefresh = {
                    val started = onRefresh()
                    if (!started) return@PullRefresh
                    scope.launch {
                        // Fake refresh status but hide it after a second as it's a long running task
                        isRefreshing = true
                        delay(1.seconds)
                        isRefreshing = false
                    }
                },
            ) {

            val wrappedGetItemsForCategory: (Category) -> List<LibraryItem> = { pageCategory ->
                if (showParentFilters) {
                    // Existing behavior when parent filters are enabled:
                    // If a specific subcategory is selected and it belongs to this parent, show that subcategory's items
                    val selectedSub = activeSubcategoryId?.let { id -> categories.firstOrNull { it.id == id } }
                    if (selectedSub != null && selectedSub.parentId == pageCategory.id) {
                        getItemsForCategory(selectedSub)
                    } else if (activeSubcategoryId == null) {
                        // "All" selected: return parent items PLUS all items from its subcategories, deduped by manga id.
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
                        // No relevant selection: fallback to parent items
                        getItemsForCategory(pageCategory)
                    }
                } else {
                    // When parent filters are disabled, ALWAYS show parent items + all subcategory items (deduped).
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
                    // Pages correspond to parentCategories
                    getCategoryForPage = { page -> parentCategories[page] },
                    getDisplayMode = getDisplayMode,
                    getColumnsForOrientation = getColumnsForOrientation,
                    // Use wrapped items getter
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
                // Reset subcategory selection when changing parent page to avoid showing a subcategory
                // from a different parent by mistake.
                activeSubcategoryId = null
                onChangeCurrentPage(pagerState.currentPage)
            }
        }
    }
}

@Composable
private fun ParentChipsRow(
    parents: List<Category>,
    activeParentId: Long?,
    collapsedParentIds: Set<Long>,
    onParentChange: (Long?) -> Unit,
    onToggleCollapse: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)
            .horizontalScroll(rememberScrollState())
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = activeParentId == null,
            onClick = { onParentChange(null) },
            label = { Text(stringResource(MR.strings.all)) },
        )
        parents.sortedBy { it.order }.forEach { parent ->
            val isCollapsed = collapsedParentIds.contains(parent.id)
            FilterChip(
                selected = activeParentId == parent.id,
                onClick = { onParentChange(parent.id) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(parent.visualName)
                        Text(
                            text = if (isCollapsed) "▼" else "▲",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable(
                                enabled = true,
                                onClick = { onToggleCollapse(parent.id) },
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ParentCategoryMenu(
    parents: List<Category>,
    activeParentId: Long?,
    onParentChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedParents = remember(parents) { parents.sortedBy { it.order } }
    val defaultLabel = stringResource(MR.strings.categories)
    val currentParent = remember(activeParentId, sortedParents) {
        sortedParents.firstOrNull { it.id == activeParentId }
    }
    val currentLabel = if (currentParent != null) currentParent.visualName else defaultLabel

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(MR.strings.categories),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(currentLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.none)) },
                    onClick = {
                        onParentChange(null)
                        expanded = false
                    },
                )
                sortedParents.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.visualName) },
                        onClick = {
                            onParentChange(parent.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
