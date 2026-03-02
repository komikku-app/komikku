package eu.kanade.presentation.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DuplicateMangaDialog(
    duplicates: List<MangaWithChapterCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: (manga: Manga) -> Unit,
    onMigrate: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    targetManga: Manga,
    bulkFavoriteManga: Manga? = null,
    onAllowAllDuplicate: () -> Unit = {},
    onSkipAllDuplicate: () -> Unit = {},
    onSkipDuplicate: () -> Unit = {},
    stopRunning: () -> Unit = {},
    // KMK <--
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val minHeight = LocalPreferenceMinHeight.current
    val horizontalPadding = PaddingValues(horizontal = TabbedDialogPaddings.Horizontal)
    val horizontalPaddingModifier = Modifier.padding(horizontalPadding)

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = {
            // KMK -->
            stopRunning()
            // KMK <--
            onDismissRequest()
        },
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.possible_duplicates_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .then(horizontalPaddingModifier)
                    .padding(top = MaterialTheme.padding.small),
            )

            // KMK -->
            Text(
                text = targetManga.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.then(horizontalPaddingModifier),
            )
            // KMK <--

            Text(
                text = stringResource(MR.strings.possible_duplicates_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.then(horizontalPaddingModifier),
            )

            LazyRow(
                horizontalArrangement = /* KMK --> */ Arrangement.SpaceAround, /* KMK <-- */
                modifier = Modifier.height(getMaximumMangaCardHeight(duplicates))
                    // KMK -->
                    .fillMaxWidth(),
                // KMK <--
                contentPadding = horizontalPadding,
            ) {
                // KMK -->
                itemsIndexed(
                    // KMK <--
                    items = duplicates,
                    key = { _, it -> it.manga.id },
                ) { index, it ->
                    DuplicateMangaListItem(
                        duplicate = it,
                        getSource = { sourceManager.getOrStub(it.manga.source) },
                        onMigrate = { onMigrate(it.manga) },
                        onOpenManga = { onOpenManga(it.manga) },
                    )

                    // KMK -->
                    if (index != duplicates.lastIndex) {
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                    }
                    // KMK <--
                }
            }

            // KMK -->
            if (bulkFavoriteManga != null) {
                Column(
                    modifier = horizontalPaddingModifier
                        .padding(bottom = MaterialTheme.padding.medium),
                ) {
                    HorizontalDivider()

                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onConfirm()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_add_anyway))
                            }

                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onAllowAllDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_allow_all_duplicate_manga))
                            }
                        }

                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onSkipDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_skip_duplicate_manga))
                            }

                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onSkipAllDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_skip_all_duplicate_manga))
                            }
                        }

                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onOpenManga(bulkFavoriteManga)
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_show_manga))
                            }

                            TextButton(
                                onClick = {
                                    stopRunning()
                                    onDismissRequest()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        }
                    }
                }
            } else {
                // KMK <--
                Column(modifier = horizontalPaddingModifier) {
                    HorizontalDivider()

                    TextPreferenceWidget(
                        title = stringResource(MR.strings.action_add_anyway),
                        icon = Icons.Outlined.Add,
                        onPreferenceClick = {
                            onDismissRequest()
                            onConfirm()
                        },
                        modifier = Modifier.clip(CircleShape),
                    )
                }

                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .then(horizontalPaddingModifier)
                        .padding(bottom = MaterialTheme.padding.medium)
                        .heightIn(min = minHeight)
                        .fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall),
                        text = stringResource(MR.strings.action_cancel),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateMangaListItem(
    duplicate: MangaWithChapterCount,
    getSource: () -> Source,
    onOpenManga: () -> Unit,
    onMigrate: () -> Unit,
) {
    // KMK -->
    val usePanoramaCover by Injekt.get<UiPreferences>().usePanoramaCoverAlways().collectAsState()
    val coverRatio = remember { mutableFloatStateOf(1f) }
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
    // KMK <--

    val source = getSource()
    val manga = duplicate.manga
    Column(
        modifier = Modifier
            // KMK -->
            .then(
                if (usePanoramaCover && coverIsWide) {
                    Modifier.width(MangaCardPanoramaWidth)
                } else {
                    Modifier.width(MangaCardWidth)
                },
            )
            // KMK <--
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onLongClick = { onOpenManga() },
                onClick = { onMigrate() },
            )
            .padding(MaterialTheme.padding.small),
    ) {
        // KMK -->
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            if (usePanoramaCover && coverIsWide) {
                MangaCover.Panorama(
                    modifier = Modifier
                        .align(Alignment.Center),
                    data = manga,
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                )
            } else {
                MangaCover.Book(
                    modifier = Modifier
                        .align(Alignment.Center),
                    data = manga,
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                )
            }
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                Badge(
                    color = MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary,
                    text = pluralStringResource(
                        MR.plurals.manga_num_chapters,
                        duplicate.chapterCount.toInt(),
                        duplicate.chapterCount,
                    ),
                )
            }
        }
        // KMK <--

        Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))

        Text(
            text = manga.title,
            style = MaterialTheme.typography.titleSmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )

        if (!manga.author.isNullOrBlank()) {
            MangaDetailRow(
                text = manga.author!!,
                iconImageVector = Icons.Filled.PersonOutline,
                maxLines = 2,
            )
        }

        if (!manga.artist.isNullOrBlank() && manga.author != manga.artist) {
            MangaDetailRow(
                text = manga.artist!!,
                iconImageVector = Icons.Filled.Brush,
                maxLines = 2,
            )
        }

        MangaDetailRow(
            text = when (manga.status) {
                SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
                SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
                SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
                SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
                SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
                SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
                else -> stringResource(MR.strings.unknown)
            },
            iconImageVector = when (manga.status) {
                SManga.ONGOING.toLong() -> Icons.Outlined.Schedule
                SManga.COMPLETED.toLong() -> Icons.Outlined.DoneAll
                SManga.LICENSED.toLong() -> Icons.Outlined.AttachMoney
                SManga.PUBLISHING_FINISHED.toLong() -> Icons.Outlined.Done
                SManga.CANCELLED.toLong() -> Icons.Outlined.Close
                SManga.ON_HIATUS.toLong() -> Icons.Outlined.Pause
                else -> Icons.Outlined.Block
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (source is StubSource) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = source.name,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MangaDetailRow(
    text: String,
    iconImageVector: ImageVector,
    maxLines: Int = 1,
) {
    Row(
        modifier = Modifier
            .secondaryItemAlpha()
            .padding(top = MaterialTheme.padding.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconImageVector,
            contentDescription = null,
            modifier = Modifier.size(MangaDetailsIconWidth),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
        )
    }
}

@Composable
private fun getMaximumMangaCardHeight(duplicates: List<MangaWithChapterCount>): Dp {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val textMeasurer = rememberTextMeasurer()

    val smallPadding = with(density) { MaterialTheme.padding.small.roundToPx() }
    val extraSmallPadding = with(density) { MaterialTheme.padding.extraSmall.roundToPx() }

    val width = with(density) { MangaCardWidth.roundToPx() - (2 * smallPadding) }
    val iconWidth = with(density) { MangaDetailsIconWidth.roundToPx() }

    val coverHeight = width / MangaCover.Book.ratio
    val constraints = Constraints(maxWidth = width)
    val detailsConstraints = Constraints(maxWidth = width - iconWidth - extraSmallPadding)

    return remember(
        duplicates,
        density,
        typography,
        textMeasurer,
        smallPadding,
        extraSmallPadding,
        coverHeight,
        constraints,
        detailsConstraints,
    ) {
        duplicates.fastMaxOfOrNull {
            calculateMangaCardHeight(
                manga = it.manga,
                density = density,
                typography = typography,
                textMeasurer = textMeasurer,
                smallPadding = smallPadding,
                extraSmallPadding = extraSmallPadding,
                coverHeight = coverHeight,
                constraints = constraints,
                detailsConstraints = detailsConstraints,
            )
        }
            ?: 0.dp
    }
}

private fun calculateMangaCardHeight(
    manga: Manga,
    density: Density,
    typography: Typography,
    textMeasurer: TextMeasurer,
    smallPadding: Int,
    extraSmallPadding: Int,
    coverHeight: Float,
    constraints: Constraints,
    detailsConstraints: Constraints,
): Dp {
    val titleHeight = textMeasurer.measureHeight(manga.title, typography.titleSmall, 2, constraints)
    val authorHeight = if (!manga.author.isNullOrBlank()) {
        textMeasurer.measureHeight(manga.author!!, typography.bodySmall, 2, detailsConstraints)
    } else {
        0
    }
    val artistHeight = if (!manga.artist.isNullOrBlank() && manga.author != manga.artist) {
        textMeasurer.measureHeight(manga.artist!!, typography.bodySmall, 2, detailsConstraints)
    } else {
        0
    }
    val statusHeight = textMeasurer.measureHeight("", typography.bodySmall, 2, detailsConstraints)
    val sourceHeight = textMeasurer.measureHeight("", typography.labelSmall, 1, constraints)

    val totalHeight = coverHeight + titleHeight + authorHeight + artistHeight + statusHeight + sourceHeight
    return with(density) { ((2 * smallPadding) + totalHeight + (5 * extraSmallPadding)).toDp() }
}

private fun TextMeasurer.measureHeight(
    text: String,
    style: TextStyle,
    maxLines: Int,
    constraints: Constraints,
): Int = measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = maxLines,
    constraints = constraints,
)
    .size
    .height

private val MangaCardWidth = 152.dp
private val MangaDetailsIconWidth = 16.dp

// KMK -->
private val MangaCardPanoramaWidth = 322.dp
// KMK <--
