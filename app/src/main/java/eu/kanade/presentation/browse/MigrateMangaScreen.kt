package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.selectedBackground
import kotlin.time.Duration.Companion.seconds

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateMangaScreenModel.State,
    onClickItem: (MigrateMangaItem) -> Unit,
    onClickCover: (MigrateMangaItem) -> Unit,
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
            MigrateMangaScreenAppBar(
                title = title,
                actionModeCounter = state.selected.size,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            MigrateMangaScreenBottomBar(
                selected = state.selected,
                itemCount = state.titles.size,
                enableScrollToTop = enableScrollToTop,
                enableScrollToBottom = enableScrollToBottom,
                onMultiMigrateClicked = onMultiMigrateClicked,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = onInvertSelection,
                onCancelActionMode = { onSelectAll(false) },
                navigateUp = navigateUp,
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
            onMangaSelected = onMangaSelected,
            listState = listState,
        )
    }
}

@Composable
private fun MigrateMangaContent(
    contentPadding: PaddingValues,
    state: MigrateMangaScreenModel.State,
    onClickItem: (MigrateMangaItem) -> Unit,
    onClickCover: (MigrateMangaItem) -> Unit,
    onMangaSelected: (MigrateMangaItem, Boolean, Boolean, Boolean) -> Unit,
    listState: LazyListState,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
        state = listState,
    ) {
        items(
            items = state.titles,
        ) {
            MigrateMangaScreenUiItem(
                modifier = Modifier.animateItemFastScroll(),
                manga = it.manga,
                selected = it.selected,
                onClick = {
                    when {
                        state.selectionMode -> onMangaSelected(it, !it.selected, true, false)
                        else -> onClickItem(it)
                    }
                },
                onLongClick = { onMangaSelected(it, !it.selected, true, true) },
                onClickCover = { onClickCover(it) }.takeIf { !state.selectionMode },
            )
        }
    }
}

@Composable
private fun MigrateMangaScreenUiItem(
    modifier: Modifier,
    manga: Manga,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .height(48.dp),
            data = manga,
            onClick = onClickCover,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 5.dp)
                .weight(1f),
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

@Composable
private fun MigrateMangaScreenAppBar(
    modifier: Modifier = Modifier,
    title: String?,
    actionModeCounter: Int,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val isActionMode by remember(actionModeCounter) {
        derivedStateOf { actionModeCounter > 0 }
    }

    Column(
        modifier = modifier,
    ) {
        TopAppBar(
            title = {
                if (isActionMode) {
                    AppBarTitle("$actionModeCounter selected")
                } else {
                    AppBarTitle(title)
                }
            },
            actions = {},
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    elevation = if (isActionMode) 3.dp else 0.dp,
                ),
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun MigrateMangaScreenBottomBar(
    modifier: Modifier = Modifier,
    selected: List<MigrateMangaItem>,
    itemCount: Int,
    enableScrollToTop: Boolean,
    enableScrollToBottom: Boolean,
    onMultiMigrateClicked: (() -> Unit),
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    navigateUp: () -> Unit,
    scrollToTop: () -> Unit,
    scrollToBottom: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animatedElevation by animateDpAsState(if (selected.isNotEmpty()) 3.dp else 0.dp)
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
        val confirm = remember { mutableStateListOf(false, false, false, false, false, false) }
        var resetJob: Job? = remember { null }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            (0 until 6).forEach { i -> confirm[i] = i == toConfirmIndex }
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
            if (selected.isNotEmpty()) {
                Button(
                    title = stringResource(R.string.action_cancel),
                    icon = Icons.Outlined.Close,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = onCancelActionMode,
                    enabled = true,
                )
            } else {
                Button(
                    title = stringResource(R.string.abc_action_bar_up_description),
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = navigateUp,
                    enabled = true,
                )
            }
            Button(
                title = stringResource(R.string.action_select_all),
                icon = Icons.Outlined.SelectAll,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
                onClick = if (selected.isEmpty() or (selected.size != itemCount)) {
                    onSelectAll
                } else {
                    {}
                },
                enabled = selected.isEmpty() or (selected.size != itemCount),
            )
            Button(
                title = stringResource(R.string.action_select_inverse),
                icon = Icons.Outlined.FlipToBack,
                toConfirm = confirm[3],
                onLongClick = { onLongClickItem(3) },
                onClick = if (selected.isNotEmpty()) {
                    onInvertSelection
                } else {
                    {}
                },
                enabled = selected.isNotEmpty(),
            )
            Button(
                title = stringResource(R.string.action_scroll_to_top),
                icon = Icons.Outlined.ArrowUpward,
                toConfirm = confirm[4],
                onLongClick = { onLongClickItem(4) },
                onClick = if (enableScrollToTop) {
                    scrollToTop
                } else {
                    {}
                },
                enabled = enableScrollToTop,
            )
            Button(
                title = stringResource(R.string.action_scroll_to_bottom),
                icon = Icons.Outlined.ArrowDownward,
                toConfirm = confirm[5],
                onLongClick = { onLongClickItem(5) },
                onClick = if (enableScrollToBottom) {
                    scrollToBottom
                } else {
                    {}
                },
                enabled = enableScrollToBottom,
            )
            Button(
                title = stringResource(R.string.migrate),
                icon = Icons.Outlined.FindReplace,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = onMultiMigrateClicked,
                enabled = true,
            )
        }
    }
}

@Composable
private fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    enabled: Boolean,
    onLongClick: () -> Unit,
    onClick: (() -> Unit),
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(if (toConfirm) 2f else 1f)
    val animatedColor by animateColorAsState(
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f,
            )
        },
    )
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = animatedColor,
        )
        AnimatedVisibility(
            visible = toConfirm,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Visible,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor,
            )
        }
        content?.invoke()
    }
}
