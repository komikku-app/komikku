package eu.kanade.presentation.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.UpdateSelectionOptions
import eu.kanade.tachiyomi.ui.updates.groupByDateAndManga
import mihon.feature.upcoming.DateHeading
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

internal fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "updates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItemFastScroll()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

internal fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    // KMK -->
    expandedState: Set<String>,
    collapseToggle: (key: String) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
    selectionMode: Boolean,
    // SY -->
    preserveReadingPosition: Boolean,
    // SY <--
    onUpdateSelected: (UpdatesItem, /* KMK --> */ UpdateSelectionOptions /* KMK <-- */) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
) {
    items(
        items = uiModels,
        contentType = {
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
            }
        },
        key = {
            when (it) {
                is UpdatesUiModel.Header -> "updatesHeader-${it.hashCode()}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                // KMK -->
                DateHeading(
                    modifier = Modifier.animateItemFastScroll()
                        .padding(top = MaterialTheme.padding.extraSmall),
                    date = item.date,
                    mangaCount = item.mangaCount,
                )
                // KMK <--
            }
            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                // KMK -->
                val isLeader = item is UpdatesUiModel.Leader
                val isExpanded = expandedState.contains(updatesItem.update.groupByDateAndManga())

                AnimatedVisibility(
                    visible = isLeader || isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // KMK <--
                    UpdatesUiItem(
                        modifier = Modifier.animateItemFastScroll(),
                        update = updatesItem.update,
                        selected = updatesItem.selected,
                        readProgress = updatesItem.update.lastPageRead
                            .takeIf {
                                /* SY --> */(
                                    !updatesItem.update.read ||
                                        (preserveReadingPosition && updatesItem.isEhBasedUpdate())
                                    )/* SY <-- */ &&
                                    it > 0L
                            }
                            ?.let {
                                stringResource(
                                    MR.strings.chapter_progress,
                                    it + 1,
                                )
                            },
                        onLongClick = {
                            onUpdateSelected(
                                updatesItem,
                                // KMK -->
                                UpdateSelectionOptions(
                                    selected = !updatesItem.selected,
                                    userSelected = true,
                                    fromLongPress = true,
                                    isGroup = isLeader && item.isExpandable,
                                    isExpanded = isExpanded,
                                ),
                                // KMK <--
                            )
                        },
                        onClick = {
                            when {
                                selectionMode -> onUpdateSelected(
                                    updatesItem,
                                    // KMK -->
                                    UpdateSelectionOptions(
                                        selected = !updatesItem.selected,
                                        userSelected = true,
                                        fromLongPress = false,
                                        isGroup = isLeader && item.isExpandable,
                                        isExpanded = isExpanded,
                                    ),
                                    // KMK <--
                                )
                                else -> onClickUpdate(updatesItem)
                            }
                        },
                        onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                        onDownloadChapter = { action: ChapterDownloadAction ->
                            onDownloadChapter(listOf(updatesItem), action)
                        }.takeIf { !selectionMode },
                        downloadStateProvider = updatesItem.downloadStateProvider,
                        downloadProgressProvider = updatesItem.downloadProgressProvider,
                        // KMK -->
                        isLeader = isLeader,
                        isExpandable = item.isExpandable,
                        expanded = isExpanded,
                        collapseToggle = collapseToggle,
                        usePanoramaCover = usePanoramaCover,
                        // KMK <--
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    // KMK -->
    isLeader: Boolean,
    isExpandable: Boolean,
    expanded: Boolean,
    collapseToggle: (key: String) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
    modifier: Modifier = Modifier,
    // KMK -->
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    // KMK <--
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) DISABLED_ALPHA else 1f

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
            .padding(
                // KMK -->
                vertical = if (isLeader) MaterialTheme.padding.extraSmall else 0.dp,
                // KMK <--
                horizontal = MaterialTheme.padding.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK -->
        val mangaCover = update.coverData
        val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
        val bgColor = mangaCover.dominantCoverColors?.first?.let { Color(it) }
        val onBgColor = mangaCover.dominantCoverColors?.second
        if (isLeader) {
            if (usePanoramaCover && coverIsWide) {
                MangaCover.Panorama(
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small)
                        .width(UpdateItemPanoramaWidth),
                    data = mangaCover,
                    onClick = onClickCover,
                    // KMK -->
                    bgColor = bgColor,
                    tint = onBgColor,
                    size = MangaCover.Size.Medium,
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                    // KMK <--
                )
            } else {
                // KMK <--
                MangaCover.Book(
                    modifier = Modifier
                        // KMK -->
                        .padding(top = MaterialTheme.padding.small)
                        .width(UpdateItemWidth),
                    // KMK <--
                    data = mangaCover,
                    onClick = onClickCover,
                    // KMK -->
                    bgColor = bgColor,
                    tint = onBgColor,
                    size = MangaCover.Size.Medium,
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(if (usePanoramaCover && coverIsWide) UpdateItemPanoramaWidth else UpdateItemWidth),
            )
            // KMK <--
        }

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            // KMK -->
            if (isLeader) {
                // KMK <--
                Text(
                    text = update.mangaTitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableIntStateOf(0) }
                if (!update.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(MR.strings.unread),
                        modifier = Modifier
                            .height(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier
                        .weight(weight = 1f, fill = false),
                )
                if (readProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = readProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // KMK -->
        if (isLeader && isExpandable) {
            CollapseButton(
                expanded = expanded,
                collapseToggle = { collapseToggle(update.groupByDateAndManga()) },
            )
        }
        // KMK <--

        ChapterDownloadIndicator(
            enabled = onDownloadChapter != null,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadChapter?.invoke(it) },
        )
    }
}

// KMK -->
@Composable
fun CollapseButton(
    expanded: Boolean,
    collapseToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAnimatedVectorPainter(
        AnimatedImageVector.animatedVectorResource(R.drawable.anim_caret_down),
        !expanded,
    )

    Box(
        modifier = modifier
            .size(IndicatorSize + MaterialTheme.padding.extraSmall),
        contentAlignment = Alignment.TopCenter,
    ) {
        IconButton(
            onClick = { collapseToggle() },
            modifier = Modifier.size(IndicatorSize),
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val IndicatorSize = MaterialTheme.padding.large

private val UpdateItemPanoramaWidth = 126.dp // Book cover
private val UpdateItemWidth = 56.dp
// KMK <--
