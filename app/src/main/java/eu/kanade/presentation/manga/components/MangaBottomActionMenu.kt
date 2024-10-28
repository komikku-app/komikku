package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.isTabletUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaBottomActionMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onMarkAsReadClicked: (() -> Unit)? = null,
    onMarkAsUnreadClicked: (() -> Unit)? = null,
    onMarkPreviousAsReadClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false, false, false, false, false) }
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (0..<7).forEach { i -> confirm[i] = i == toConfirmIndex }
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
                if (onBookmarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_bookmark),
                        icon = Icons.Outlined.BookmarkAdd,
                        toConfirm = confirm[0],
                        onLongClick = { onLongClickItem(0) },
                        onClick = onBookmarkClicked,
                    )
                }
                if (onRemoveBookmarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_remove_bookmark),
                        icon = Icons.Outlined.BookmarkRemove,
                        toConfirm = confirm[1],
                        onLongClick = { onLongClickItem(1) },
                        onClick = onRemoveBookmarkClicked,
                    )
                }
                if (onMarkAsReadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_read),
                        icon = Icons.Outlined.DoneAll,
                        toConfirm = confirm[2],
                        onLongClick = { onLongClickItem(2) },
                        onClick = onMarkAsReadClicked,
                    )
                }
                if (onMarkAsUnreadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_unread),
                        icon = Icons.Outlined.RemoveDone,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = onMarkAsUnreadClicked,
                    )
                }
                if (onMarkPreviousAsReadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_previous_as_read),
                        icon = ImageVector.vectorResource(R.drawable.ic_done_prev_24dp),
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = onMarkPreviousAsReadClicked,
                    )
                }
                if (onDownloadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = onDownloadClicked,
                    )
                }
                if (onDeleteClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        toConfirm = confirm[6],
                        onLongClick = { onLongClickItem(6) },
                        onClick = onDeleteClicked,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    // KMK -->
    enabled: Boolean = true,
    // KMK <--
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (toConfirm) 2f else 1f,
        label = "weight",
    )
    // KMK -->
    val animatedColor by animateColorAsState(
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f,
            )
        },
        label = "color",
    )
    // KMK <--
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                interactionSource = null,
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
            // KMK -->
            tint = animatedColor,
            // KMK <--
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
                // KMK -->
                color = animatedColor,
                // KMK <--
            )
        }
        content?.invoke()
    }
}

@Composable
fun LibraryBottomActionMenu(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onDeleteClicked: () -> Unit,
    // SY -->
    onClickCleanTitles: (() -> Unit)?,
    onClickMigrate: (() -> Unit)?,
    onClickAddToMangaDex: (() -> Unit)?,
    onClickResetInfo: (() -> Unit)?,
    // SY <--
    // KMK -->
    onClickMerge: (() -> Unit)?,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm =
                remember {
                    mutableStateListOf(false, false, false, false, false /* SY --> */, false, false, false /* SY <-- */)
                }
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (0..<8).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            // SY -->
            val showOverflow = onClickCleanTitles != null ||
                onClickAddToMangaDex != null ||
                onClickResetInfo != null ||
                // KMK -->
                onClickMigrate != null ||
                onClickMerge != null
            // KMK <--
            val configuration = LocalConfiguration.current
            val isTabletUi = remember { configuration.isTabletUi() }
            var overFlowOpen by remember { mutableStateOf(false) }
            // SY <--
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(MR.strings.action_move_category),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = onChangeCategoryClicked,
                )
                if (onDownloadClicked != null) {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = { downloadExpanded = !downloadExpanded },
                    ) {
                        val onDismissRequest = { downloadExpanded = false }
                        DownloadDropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = onDismissRequest,
                            onDownloadClicked = onDownloadClicked,
                        )
                    }
                }
                Button(
                    title = stringResource(MR.strings.action_delete),
                    icon = Icons.Outlined.Delete,
                    toConfirm = confirm[4],
                    onLongClick = { onLongClickItem(4) },
                    onClick = onDeleteClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_read),
                    icon = Icons.Outlined.DoneAll,
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                    onClick = onMarkAsReadClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_unread),
                    icon = Icons.Outlined.RemoveDone,
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                    onClick = onMarkAsUnreadClicked,
                )
                // SY -->
                if (showOverflow) {
                    if (isTabletUi) {
                        if (onClickMigrate != null) {
                            Button(
                                title = stringResource(MR.strings.migrate),
                                icon = Icons.Outlined.SwapCalls,
                                toConfirm = confirm[6],
                                onLongClick = { onLongClickItem(6) },
                                onClick = onClickMigrate,
                            )
                        }
                        // KMK -->
                        if (onClickMerge != null) {
                            Button(
                                title = stringResource(SYMR.strings.merge),
                                icon = Icons.Outlined.Merge,
                                toConfirm = confirm[7],
                                onLongClick = { onLongClickItem(7) },
                                onClick = onClickMerge,
                            )
                        }
                        // KMK <--
                    }
                    Button(
                        title = stringResource(MR.strings.label_more),
                        icon = Icons.Outlined.MoreVert,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = { overFlowOpen = true },
                    )
                    DropdownMenu(
                        expanded = overFlowOpen,
                        onDismissRequest = { overFlowOpen = false },
                        // KMK -->
                        offset = DpOffset((-10).dp, 0.dp),
                        // KMK <--
                    ) {
                        if (!isTabletUi) {
                            if (onClickMigrate != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.migrate)) },
                                    onClick = onClickMigrate,
                                )
                            }
                            // KMK -->
                            if (onClickMerge != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(SYMR.strings.merge)) },
                                    onClick = onClickMerge,
                                )
                            }
                            // KMK <--
                        }
                        if (onClickCleanTitles != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(SYMR.strings.action_clean_titles)) },
                                onClick = onClickCleanTitles,
                            )
                        }
                        if (onClickAddToMangaDex != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(SYMR.strings.mangadex_add_to_follows)) },
                                onClick = onClickAddToMangaDex,
                            )
                        }
                        if (onClickResetInfo != null) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(SYMR.strings.reset_info)) },
                                onClick = onClickResetInfo,
                            )
                        }
                    }
                    /* KMK -->
                } else {
                    if (onClickMigrate != null) {
                        Button(
                            title = stringResource(MR.strings.migrate),
                            icon = Icons.Outlined.SwapCalls,
                            toConfirm = confirm[6],
                            onLongClick = { onLongClickItem(6) },
                            onClick = onClickMigrate,
                        )
                    }
                    if (onClickMerge != null) {
                        Button(
                            title = stringResource(SYMR.strings.merge),
                            icon = Icons.Outlined.Merge,
                            toConfirm = confirm[7],
                            onLongClick = { onLongClickItem(7) },
                            onClick = onClickMerge,
                        )
                    }
                    // KMK <-- */
                }
                // SY <--
            }
        }
    }
}
