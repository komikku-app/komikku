package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    // KMK -->
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onChangeSearchQuery: (String?) -> Unit,
    onReorderGroup: (String, Int) -> Unit,
    onReorderSource: (String, Long, Int) -> Unit,
    // KMK <--
) {
    // KMK -->
    val lazyListState = rememberLazyListState()

    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) {
        onChangeSearchQuery("")
    }
    // KMK <--

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        // KMK -->
        state.searchQuery == null &&
            // KMK <--
            state.isEmpty -> EmptyScreen(
            MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        // KMK -->
        else -> Box(
            modifier = Modifier.padding(contentPadding),
        ) {
            val density = LocalDensity.current
            var searchBoxHeight by remember { mutableStateOf(SOURCE_SEARCH_BOX_HEIGHT) }

            if (state.reorderMode) {
                // Reorder mode: flat list with drag handles on both group headers and source items.
                // Headers move the entire group block; items only move within their own group.
                val itemsState = remember { state.items.toMutableStateList() }
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val fromItem = itemsState.getOrNull(from.index)
                    val toItem = itemsState.getOrNull(to.index)
                    if (fromItem == null || toItem == null) return@rememberReorderableLazyListState

                    val isMovingHeader = fromItem is SourceUiModel.Header
                    val isTargetHeader = toItem is SourceUiModel.Header

                    if (isMovingHeader) {
                        val fromHeader = fromItem as SourceUiModel.Header
                        val fromGroupKey = SourcesScreenModel.groupKeyOf(fromHeader)

                        // Pinned and last_used groups are fixed — skip reordering
                        if (fromGroupKey == SourcesScreenModel.PINNED_KEY ||
                            fromGroupKey == SourcesScreenModel.LAST_USED_KEY
                        ) {
                            return@rememberReorderableLazyListState
                        }

                        // Find the range of the source group
                        var groupEnd = from.index
                        for (i in (from.index + 1) until itemsState.size) {
                            if (itemsState[i] is SourceUiModel.Header) break
                            groupEnd = i
                        }
                        val groupSize = groupEnd - from.index + 1

                        // Find target group position (the header we're dropping onto)
                        val targetHeaderIndex = if (isTargetHeader) {
                            to.index
                        } else {
                            // Find the header above the target item
                            var headerIdx = to.index
                            while (headerIdx > 0 && itemsState[headerIdx] !is SourceUiModel.Header) {
                                headerIdx--
                            }
                            headerIdx
                        }

                        val targetHeader = itemsState.getOrNull(targetHeaderIndex) as? SourceUiModel.Header
                            ?: return@rememberReorderableLazyListState
                        val targetGroupKey = SourcesScreenModel.groupKeyOf(targetHeader)

                        if (fromGroupKey == targetGroupKey) return@rememberReorderableLazyListState

                        // Remove the source group block
                        val groupBlock = itemsState.subList(from.index, from.index + groupSize).toList()
                        repeat(groupSize) { itemsState.removeAt(from.index) }

                        // Find new insertion index (target header may have shifted)
                        val newHeaderIndex = itemsState.indexOfFirst {
                            it is SourceUiModel.Header && SourcesScreenModel.groupKeyOf(it) == targetGroupKey
                        }
                        if (newHeaderIndex == -1) return@rememberReorderableLazyListState

                        // Insert before or after target based on drag direction
                        val insertIndex = if (to.index > from.index) {
                            // Dragging down: insert after target group
                            var afterGroup = newHeaderIndex
                            for (i in (newHeaderIndex + 1) until itemsState.size) {
                                if (itemsState[i] is SourceUiModel.Header) break
                                afterGroup = i
                            }
                            afterGroup + 1
                        } else {
                            // Dragging up: insert before target group
                            newHeaderIndex
                        }

                        itemsState.addAll(insertIndex.coerceIn(0, itemsState.size), groupBlock)

                        // Compute the new group index and save
                        val newGroupIndex = itemsState.indexOfFirst {
                            it is SourceUiModel.Header && SourcesScreenModel.groupKeyOf(it) == fromGroupKey
                        }
                        // Count how many headers are before this one
                        val groupIndex = itemsState.take(newGroupIndex)
                            .count { it is SourceUiModel.Header }
                        onReorderGroup(fromGroupKey, groupIndex)
                    } else {
                        // Moving a source item within its group
                        val sourceItem = fromItem as SourceUiModel.Item
                        if (isTargetHeader) return@rememberReorderableLazyListState

                        // Find the group (header) for both from and to
                        var fromHeaderIdx = from.index
                        while (fromHeaderIdx > 0 && itemsState[fromHeaderIdx] !is SourceUiModel.Header) {
                            fromHeaderIdx--
                        }
                        val fromHeader = itemsState.getOrNull(fromHeaderIdx) as? SourceUiModel.Header
                            ?: return@rememberReorderableLazyListState
                        val groupKey = SourcesScreenModel.groupKeyOf(fromHeader)

                        // Check that target is in the same group
                        var toHeaderIdx = to.index
                        while (toHeaderIdx > 0 && itemsState[toHeaderIdx] !is SourceUiModel.Header) {
                            toHeaderIdx--
                        }
                        if (toHeaderIdx != fromHeaderIdx) return@rememberReorderableLazyListState

                        // Move the item in the list (adjust index after removal)
                        val targetIndex = if (from.index < to.index) to.index - 1 else to.index
                        itemsState.removeAt(from.index)
                        itemsState.add(targetIndex.coerceIn(0, itemsState.size), sourceItem)

                        // Compute new index within the group
                        val groupStart = fromHeaderIdx + 1
                        val newIndex = targetIndex - groupStart
                        onReorderSource(groupKey, sourceItem.source.id, newIndex)
                    }
                }

                LaunchedEffect(state.items) {
                    if (!reorderableState.isAnyItemDragging) {
                        itemsState.clear()
                        itemsState.addAll(state.items)
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = searchBoxHeight),
                ) {
                    itemsState.forEachIndexed { index, model ->
                        when (model) {
                            is SourceUiModel.Header -> {
                                val groupKey = SourcesScreenModel.groupKeyOf(model)
                                val isFixed = groupKey == SourcesScreenModel.PINNED_KEY ||
                                    groupKey == SourcesScreenModel.LAST_USED_KEY
                                item(
                                    key = "header-${model.hashCode()}",
                                    contentType = "header",
                                ) {
                                    ReorderableItem(reorderableState, key = "header-${model.hashCode()}") {
                                        SourceHeader(
                                            modifier = Modifier
                                                .animateItem()
                                                .background(MaterialTheme.colorScheme.background)
                                                .fillMaxWidth(),
                                            language = model.language,
                                            isCategory = model.isCategory,
                                            showDragHandle = !isFixed,
                                            dragModifier = if (isFixed) Modifier else Modifier.draggableHandle(),
                                        )
                                    }
                                }
                            }
                            is SourceUiModel.Item -> {
                                item(
                                    key = "source-${model.source.key()}",
                                    contentType = "item",
                                ) {
                                    ReorderableItem(reorderableState, key = "source-${model.source.key()}") {
                                        SourceItem(
                                            modifier = Modifier.animateItem(),
                                            source = model.source,
                                            showLatest = state.showLatest,
                                            showPin = state.showPin,
                                            onClickItem = onClickItem,
                                            onLongClickItem = onLongClickItem,
                                            onClickPin = onClickPin,
                                            showDragHandle = true,
                                            dragModifier = Modifier.draggableHandle(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                FastScrollLazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = searchBoxHeight),
                ) {
                    state.items.forEach { model ->
                        when (model) {
                            is SourceUiModel.Header -> {
                                stickyHeader(
                                    key = "$STICKY_HEADER_KEY_PREFIX-header-${model.hashCode()}",
                                    contentType = "header",
                                ) {
                                    SourceHeader(
                                        modifier = Modifier
                                            .animateItemFastScroll()
                                            .background(MaterialTheme.colorScheme.background)
                                            .fillMaxWidth(),
                                        language = model.language,
                                        // SY -->
                                        isCategory = model.isCategory,
                                        // SY <--
                                    )
                                }
                            }
                            is SourceUiModel.Item -> {
                                item(
                                    key = "source-${model.source.key()}",
                                    contentType = "item",
                                ) {
                                    SourceItem(
                                        modifier = Modifier.animateItemFastScroll(),
                                        source = model.source,
                                        // SY -->
                                        showLatest = state.showLatest,
                                        showPin = state.showPin,
                                        // SY <--
                                        onClickItem = onClickItem,
                                        onLongClickItem = onLongClickItem,
                                        onClickPin = onClickPin,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // KMK -->
            AnimatedFloatingSearchBox(
                listState = lazyListState,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                placeholderText = stringResource(KMR.strings.action_search_for_source),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    )
                    .align(Alignment.TopCenter),
                onGloballyPositioned = { layoutCoordinates ->
                    searchBoxHeight = with(density) { layoutCoordinates.size.height.toDp() + 2 * MaterialTheme.padding.small }
                },
            )
            // KMK <--
        }
    }
}

@Composable
private fun SourceHeader(
    language: String,
    // SY -->
    isCategory: Boolean,
    // SY <--
    // KMK -->
    showDragHandle: Boolean = false,
    dragModifier: Modifier = Modifier,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK -->
        if (showDragHandle) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = MaterialTheme.padding.small)
                    .then(dragModifier),
            )
        }
        // KMK <--
        Text(
            // SY -->
            text = if (!isCategory) {
                LocaleHelper.getSourceDisplayName(language, context)
            } else {
                language
            },
            // SY <--
            style = MaterialTheme.typography.header,
        )
    }
}

@Composable
private fun SourceItem(
    source: Source,
    // SY -->
    showLatest: Boolean,
    showPin: Boolean,
    // SY <--
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    // KMK -->
    showDragHandle: Boolean = false,
    dragModifier: Modifier = Modifier,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        // KMK -->
        icon = {
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = MaterialTheme.padding.small)
                        .then(dragModifier),
                )
            }
            SourceIcon(source = source)
        },
        // KMK <--
        action = {
            if (source.supportsLatest /* SY --> */ && showLatest /* SY <-- */) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            // SY -->
            if (showPin) {
                SourcePinButton(
                    isPinned = Pin.Pinned in source.pin,
                    onClick = { onClickPin(source) },
                )
            }
            // SY <--
        },
    )
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickSetCategories: (() -> Unit)?,
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    onDismiss: () -> Unit,
    // KMK -->
    onClickSettings: (() -> Unit)? = null,
    // KMK <--
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickSetCategories != null) {
                    Text(
                        text = stringResource(MR.strings.categories),
                        modifier = Modifier
                            .clickable(onClick = onClickSetCategories)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(SYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(SYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
                // KMK -->
                if (onClickSettings != null &&
                    source.installedExtension !== null &&
                    source.id !in listOf(LocalSource.ID, EH_SOURCE_ID, EXH_SOURCE_ID)
                ) {
                    Text(
                        text = stringResource(MR.strings.label_extension_info),
                        modifier = Modifier
                            .clickable(onClick = onClickSettings)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // KMK <--
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface SourceUiModel {
    data class Item(val source: Source) : SourceUiModel
    data class Header(val language: String, val isCategory: Boolean) : SourceUiModel
}

// SY -->
@Composable
fun SourceCategoriesDialog(
    source: Source,
    categories: ImmutableList<String>,
    onClickCategories: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val newCategories = remember(source) {
        mutableStateListOf<String>().also { it += source.categories }
    }
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    LabeledCheckbox(
                        label = category,
                        checked = category in newCategories,
                        onCheckedChange = {
                            if (it) {
                                newCategories += category
                            } else {
                                newCategories -= category
                            }
                        },
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onClickCategories(newCategories.toList()) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// SY <--
