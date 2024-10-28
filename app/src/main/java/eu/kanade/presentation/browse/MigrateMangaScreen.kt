package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.manga.components.Button
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaItem
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String,
    state: MigrateMangaScreenModel.State,
    onClickItem: (MigrateMangaItem) -> Unit,
    onClickCover: (Manga) -> Unit,
    // KMK -->
    onMultiMigrateClicked: (() -> Unit),
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMangaSelected: (MigrateMangaItem, Boolean, Boolean, Boolean) -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            MigrateMangaAppBar(
                title = title,
                itemCnt = state.titles.size,
                navigateUp = navigateUp,
                selectedCount = state.selected.size,
                onClickUnselectAll = { onSelectAll(false) },
                onClickSelectAll = { onSelectAll(true) },
                onClickInvertSelection = onInvertSelection,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            MigrateMangaBottomBar(
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
                        listState.scrollToItem(state.titles.size - 1)
                    }
                },
                // KMK <--
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateMangaContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
            // KMK -->
            onMangaSelected = onMangaSelected,
            listState = listState,
            // KMK <--
        )
    }
}

@Composable
private fun MigrateMangaContent(
    contentPadding: PaddingValues,
    state: MigrateMangaScreenModel.State,
    onClickItem: (MigrateMangaItem) -> Unit,
    onClickCover: (Manga) -> Unit,
    // KMK -->
    onMangaSelected: (MigrateMangaItem, Boolean, Boolean, Boolean) -> Unit,
    listState: LazyListState,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
        state = listState,
    ) {
        // KMK <--
        items(items = state.titles) {
            MigrateMangaItem(
                manga = it.manga,
                onClickItem = {
                    // KMK -->
                    when {
                        state.selectionMode -> onMangaSelected(it, !it.selected, true, false)
                        // KMK <--
                        else -> onClickItem(it)
                    }
                },
                onClickCover = {
                    onClickCover(it.manga)
                    // KMK -->
                }.takeIf { !state.selectionMode },
                onLongClick = { onMangaSelected(it, !it.selected, true, true) },
                selected = it.selected,
                modifier = Modifier.animateItemFastScroll(),
                // KMK <--
            )
        }
    }
}

@Composable
private fun MigrateMangaItem(
    manga: Manga,
    onClickItem: () -> Unit,
    onClickCover: (() -> Unit)?,
    // KMK -->
    onLongClick: () -> Unit,
    selected: Boolean,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    BaseMangaListItem(
        modifier = modifier,
        manga = manga,
        onClickItem = onClickItem,
        onClickCover = { onClickCover?.invoke() },
        // KMK -->
        onLongClick = onLongClick,
        selected = selected,
        // KMK <--
    )
}

// KMK -->
@Composable
private fun MigrateMangaAppBar(
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

@Composable
private fun MigrateMangaBottomBar(
    modifier: Modifier = Modifier,
    selected: List<MigrateMangaItem>,
    onMultiMigrateClicked: (() -> Unit),
    enableScrollToTop: Boolean,
    enableScrollToBottom: Boolean,
    scrollToTop: () -> Unit,
    scrollToBottom: () -> Unit,
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
        val confirm = remember { mutableStateListOf(false, false, false) }
        var resetJob: Job? = remember { null }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            (0 until 3).forEach { i -> confirm[i] = i == toConfirmIndex }
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
                title = stringResource(MR.strings.migrate),
                icon = Icons.Outlined.FindReplace,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = if (selected.isNotEmpty()) {
                    onMultiMigrateClicked
                } else {
                    {}
                },
                enabled = selected.isNotEmpty(),
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_bottom),
                icon = Icons.Outlined.VerticalAlignBottom,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
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
// KMK <--
