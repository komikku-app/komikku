package eu.kanade.presentation.library.components

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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
    // Build parent list and display list depending on parent selection
    val parentCategories = remember(categories) { categories.filter { it.parentId == null && !it.isSystemCategory } }
    var activeParentId by rememberSaveable { mutableStateOf<Long?>(null) }

    val displayCategories = remember(categories, activeParentId, showParentFilters) {
        if (!showParentFilters || activeParentId == null) categories else {
            val parent = categories.firstOrNull { it.id == activeParentId }
            val children = categories.filter { it.parentId == activeParentId }
            (listOfNotNull(parent) + children).ifEmpty { categories }
        }
    }

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val pagerState = rememberPagerState(currentPage) { displayCategories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showParentFilters && parentCategories.isNotEmpty()) {
            ParentChipsRow(
                parents = parentCategories,
                activeParentId = activeParentId,
                onParentChange = { newParent ->
                    activeParentId = newParent
                    // Reset pager to first page for new selection
                    val target = 0
                    scope.launch { pagerState.scrollToPage(target) }
                },
            )
        }

        if (showPageTabs && displayCategories.isNotEmpty() && (displayCategories.size > 1 || !displayCategories.first().isSystemCategory)) {
            LaunchedEffect(displayCategories) {
                val targetPage = when {
                    displayCategories.isEmpty() -> 0
                    currentPage != pagerState.currentPage -> currentPage.coerceAtMost(displayCategories.size - 1)
                    pagerState.currentPage >= displayCategories.size -> displayCategories.size - 1
                    else -> pagerState.currentPage
                }
                if (targetPage != pagerState.currentPage) {
                    pagerState.scrollToPage(targetPage)
                }
                // KMK <--
            }
            LibraryTabs(
                categories = displayCategories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
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
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> displayCategories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getItemsForCategory = getItemsForCategory,
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
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}

@Composable
private fun ParentChipsRow(
    parents: List<Category>,
    activeParentId: Long?,
    onParentChange: (Long?) -> Unit,
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
            FilterChip(
                selected = activeParentId == parent.id,
                onClick = { onParentChange(parent.id) },
                label = { Text(parent.visualName) },
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
