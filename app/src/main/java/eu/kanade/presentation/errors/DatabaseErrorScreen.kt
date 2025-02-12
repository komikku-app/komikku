package eu.kanade.presentation.errors

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.errors.components.errorUiItems
import eu.kanade.presentation.manga.components.Button
import eu.kanade.tachiyomi.ui.errors.DatabaseErrorItem
import eu.kanade.tachiyomi.ui.errors.DatabaseErrorScreenState
import eu.kanade.tachiyomi.ui.errors.LibraryUpdateErrorItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun DatabaseErrorScreen(
    state: DatabaseErrorScreenState,
    onClick: (DatabaseErrorItem) -> Unit,
    onClickCover: (DatabaseErrorItem) -> Unit,
    onMultiMigrateClicked: (() -> Unit),
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean, Boolean) -> Unit,
    navigateUp: () -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val headerIndexes = remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(state) {
        headerIndexes.value = state.getHeaderIndexes()
    }

    val enableScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val enableScrollToBottom by remember {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    val enableScrollToPrevious by remember {
        derivedStateOf {
            headerIndexes.value.any { it < listState.firstVisibleItemIndex }
        }
    }
    val enableScrollToNext by remember {
        derivedStateOf {
            headerIndexes.value.any { it > listState.firstVisibleItemIndex }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            LibraryUpdateErrorAppBar(
                title = stringResource(
                    KMR.strings.label_library_update_errors,
                    state.items.size,
                ),
                itemCnt = state.items.size,
                navigateUp = navigateUp,
                selectedCount = state.selected.size,
                onClickUnselectAll = { onSelectAll(false) },
                onClickSelectAll = { onSelectAll(true) },
                onClickInvertSelection = onInvertSelection,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            LibraryUpdateErrorBottomBar(
                selected = state.selected,
                onMultiMigrateClicked = onMultiMigrateClicked,
                enableScrollToTop = enableScrollToTop,
                enableScrollToBottom = enableScrollToBottom,
                scrollToTop = {
                    scope.launch {
                        listState.scrollToItem(0)
                    }
                },
                scrollToBottom = {
                    scope.launch {
                        listState.scrollToItem(state.items.size - 1)
                    }
                },
                enableScrollToPrevious = enableScrollToTop && enableScrollToPrevious,
                enableScrollToNext = enableScrollToBottom && enableScrollToNext,
                scrollToPrevious = {
                    scope.launch {
                        listState.scrollToItem(
                            state.getHeaderIndexes()
                                .filter { it < listState.firstVisibleItemIndex }
                                .maxOrNull() ?: 0,
                        )
                    }
                },
                scrollToNext = {
                    scope.launch {
                        listState.scrollToItem(
                            state.getHeaderIndexes()
                                .filter { it > listState.firstVisibleItemIndex }
                                .minOrNull() ?: 0,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                message = stringResource(KMR.strings.info_empty_library_update_errors),
                modifier = Modifier.padding(contentPadding),
            )

            else -> {
                FastScrollLazyColumn(
                    // Using modifier instead of contentPadding so we can use stickyHeader
                    modifier = Modifier.padding(contentPadding),
                    state = listState,
                ) {
                    errorUiItems(
                        uiModels = state.getUiModel(),
                        selectionMode = state.selectionMode,
                        onErrorSelected = onErrorSelected,
                        onClick = onClick,
                        onClickCover = onClickCover,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorBottomBar(
    modifier: Modifier = Modifier,
    selected: List<LibraryUpdateErrorItem>,
    onMultiMigrateClicked: (() -> Unit),
    enableScrollToTop: Boolean,
    enableScrollToBottom: Boolean,
    scrollToTop: () -> Unit,
    scrollToBottom: () -> Unit,
    enableScrollToPrevious: Boolean,
    enableScrollToNext: Boolean,
    scrollToPrevious: () -> Unit,
    scrollToNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animatedElevation by animateDpAsState(
        targetValue = if (selected.isNotEmpty()) 3.dp else 0.dp,
        label = "elevation",
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large.copy(
            bottomEnd = ZeroCornerSize,
            bottomStart = ZeroCornerSize,
        ),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(
            elevation = animatedElevation,
        ),
    ) {
        val haptic = LocalHapticFeedback.current
        val confirm = remember { mutableStateListOf(false, false, false, false, false) }
        var resetJob: Job? = remember { null }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            (0 until 5).forEach { i -> confirm[i] = i == toConfirmIndex }
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(1.seconds)
                if (isActive) confirm[toConfirmIndex] = false
            }
        }
        Row(
            modifier = Modifier
                .padding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Button(
                title = stringResource(KMR.strings.action_scroll_to_top),
                icon = Icons.Outlined.VerticalAlignTop,
                toConfirm = confirm[0],
                onLongClick = { onLongClickItem(0) },
                onClick = if (enableScrollToTop) {
                    scrollToTop
                } else {
                    {}
                },
                enabled = enableScrollToTop,
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_previous),
                icon = Icons.Outlined.ArrowUpward,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = if (enableScrollToPrevious) {
                    scrollToPrevious
                } else {
                    {}
                },
                enabled = enableScrollToPrevious,
            )
            Button(
                title = stringResource(MR.strings.migrate),
                icon = Icons.Outlined.FindReplace,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
                onClick = if (selected.isNotEmpty()) {
                    onMultiMigrateClicked
                } else {
                    {}
                },
                enabled = selected.isNotEmpty(),
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_next),
                icon = Icons.Outlined.ArrowDownward,
                toConfirm = confirm[3],
                onLongClick = { onLongClickItem(3) },
                onClick = if (enableScrollToNext) {
                    scrollToNext
                } else {
                    {}
                },
                enabled = enableScrollToNext,
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_bottom),
                icon = Icons.Outlined.VerticalAlignBottom,
                toConfirm = confirm[4],
                onLongClick = { onLongClickItem(4) },
                onClick = if (enableScrollToBottom) {
                    scrollToBottom
                } else {
                    {}
                },
                enabled = enableScrollToBottom,
            )
        }
    }
}

@Composable
private fun LibraryUpdateErrorAppBar(
    title: String,
    itemCnt: Int,
    navigateUp: () -> Unit,
    selectedCount: Int,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = title,
        navigateUp = navigateUp,
        actions = {
            if (itemCnt > 0) {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_select_all),
                            icon = Icons.Outlined.SelectAll,
                            onClick = onClickSelectAll,
                        ),
                    ),
                )
            }
        },
        actionModeCounter = selectedCount,
        onCancelActionMode = onClickUnselectAll,
        actionModeActions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onClickSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onClickInvertSelection,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}
