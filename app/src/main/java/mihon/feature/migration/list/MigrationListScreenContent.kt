package mihon.feature.migration.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MigrationListScreenContent(
    items: ImmutableList<MigratingManga>,
    migrationComplete: Boolean,
    finishedCount: Int,
    getManga: suspend (MigratingManga.SearchResult.Success) -> Manga?,
    getChapterInfo: suspend (MigratingManga.SearchResult.Success) -> MigratingManga.ChapterInfo,
    getSourceName: (Manga) -> String,
    onItemClick: (Manga) -> Unit,
    onSearchManually: (MigratingManga) -> Unit,
    onSkip: (Long) -> Unit,
    onMigrate: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    openMigrationDialog: (Boolean) -> Unit,
    // KMK -->
    onCancel: (Long) -> Unit,
    navigateUp: () -> Unit,
    openOptionsDialog: () -> Unit,
    // KMK <--
) {
    Scaffold(
        topBar = { scrollBehavior ->
            val titleString = stringResource(SYMR.strings.migration)
            val title by produceState(initialValue = titleString, items, finishedCount, titleString) {
                withIOContext {
                    value = "$titleString ($finishedCount/${items.size})"
                }
            }
            AppBar(
                title = title,
                // KMK -->
                navigateUp = navigateUp,
                // KMK <--
                actions = {
                    AppBarActions(
                        persistentListOf(
                            // KMK -->
                            AppBar.Action(
                                title = stringResource(MR.strings.action_settings),
                                icon = Icons.Outlined.Settings,
                                onClick = openOptionsDialog,
                            ),
                            // KMK <--
                            AppBar.Action(
                                title = stringResource(MR.strings.copy),
                                icon = if (items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll,
                                onClick = { openMigrationDialog(true) },
                                enabled = migrationComplete,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.migrate),
                                icon = if (items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll,
                                onClick = { openMigrationDialog(false) },
                                enabled = migrationComplete,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
            items(items, key = { "migration-list-${it.manga.id}" }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItemFastScroll()
                        .padding(horizontal = 16.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MigrationListItem(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        manga = item.manga,
                        source = item.source,
                        chapterInfo = item.chapterInfo,
                        onClick = { onItemClick(item.manga) },
                    )

                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(SYMR.strings.migrating_to),
                        modifier = Modifier.weight(0.2f),
                    )

                    val result by item.searchResult.collectAsState()
                    MigrationListItemResult(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        migrationItem = item,
                        result = result,
                        getManga = getManga,
                        getChapterInfo = getChapterInfo,
                        getSourceName = getSourceName,
                        onItemClick = onItemClick,
                    )

                    MigrationListItemAction(
                        modifier = Modifier
                            .weight(0.2f),
                        result = result,
                        onSearchManually = { onSearchManually(item) },
                        onSkip = { onSkip(item.manga.id) },
                        onMigrate = { onMigrate(item.manga.id) },
                        onCopy = { onCopy(item.manga.id) },
                        // KMK -->
                        onCancel = { onCancel(item.manga.id) },
                        // KMK <--
                    )
                }
            }
        }
    }
}

@Composable
fun MigrationListItem(
    modifier: Modifier,
    manga: Manga,
    source: String,
    chapterInfo: MigratingManga.ChapterInfo,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        val context = LocalContext.current
        Box(
            Modifier.Companion.fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            MangaCover.Book(
                modifier = Modifier.Companion
                    .fillMaxWidth(),
                data = manga,
            )
            Box(
                modifier = Modifier.Companion
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.Companion.verticalGradient(
                            0f to Color.Companion.Transparent,
                            1f to Color(0xAA000000),
                        ),
                    )
                    .fillMaxHeight(0.33f)
                    .fillMaxWidth()
                    .align(Alignment.Companion.BottomCenter),
            )
            Text(
                modifier = Modifier.Companion
                    .padding(8.dp)
                    .align(Alignment.Companion.BottomStart),
                text = manga.title.ifBlank { stringResource(MR.strings.unknown) },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Companion.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.Companion.White,
                    shadow = Shadow(
                        color = Color.Companion.Black,
                        blurRadius = 4f,
                    ),
                ),
            )
            BadgeGroup(modifier = Modifier.Companion.padding(4.dp)) {
                Badge(text = "${chapterInfo.chapterCount}")
            }
        }
        Text(
            text = source,
            modifier = Modifier.Companion.padding(top = 4.dp, bottom = 1.dp, start = 8.dp),
            overflow = TextOverflow.Companion.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        val formattedLatestChapter by produceState(initialValue = "") {
            value = withIOContext {
                chapterInfo.getFormattedLatestChapter(context)
            }
        }
        Text(
            text = formattedLatestChapter,
            modifier = Modifier.Companion.padding(top = 1.dp, bottom = 4.dp, start = 8.dp),
            overflow = TextOverflow.Companion.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun MigrationListItemResult(
    modifier: Modifier,
    migrationItem: MigratingManga,
    result: MigratingManga.SearchResult,
    getManga: suspend (MigratingManga.SearchResult.Success) -> Manga?,
    getChapterInfo: suspend (MigratingManga.SearchResult.Success) -> MigratingManga.ChapterInfo,
    getSourceName: (Manga) -> String,
    onItemClick: (Manga) -> Unit,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            MigratingManga.SearchResult.Searching -> Box(
                modifier = Modifier.Companion
                    .widthIn(max = 150.dp)
                    .fillMaxSize()
                    .aspectRatio(MangaCover.Book.ratio),
                contentAlignment = Alignment.Companion.Center,
            ) {
                CircularProgressIndicator()
            }

            MigratingManga.SearchResult.NotFound -> Column(
                Modifier.Companion
                    .widthIn(max = 150.dp)
                    .fillMaxSize()
                    .padding(top = 4.dp),
            ) {
                Image(
                    painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    contentDescription = null,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .aspectRatio(MangaCover.Book.ratio)
                        .clip(MaterialTheme.shapes.extraSmall),
                    contentScale = ContentScale.Companion.Crop,
                )
                Text(
                    text = stringResource(SYMR.strings.no_alternatives_found),
                    modifier = Modifier.Companion.padding(top = 4.dp, bottom = 1.dp, start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            is MigratingManga.SearchResult.Success -> {
                val item by produceState<Triple<Manga, MigratingManga.ChapterInfo, String>?>(
                    initialValue = null,
                    migrationItem,
                    result,
                ) {
                    value = withIOContext {
                        val manga = getManga(result) ?: return@withIOContext null
                        Triple(
                            manga,
                            getChapterInfo(result),
                            getSourceName(manga),
                        )
                    }
                }
                if (item != null) {
                    val (manga, chapterInfo, source) = item!!
                    MigrationListItem(
                        modifier = Modifier.Companion.fillMaxSize(),
                        manga = manga,
                        source = source,
                        chapterInfo = chapterInfo,
                        onClick = {
                            onItemClick(manga)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun MigrationListItemAction(
    modifier: Modifier,
    result: MigratingManga.SearchResult,
    onSearchManually: () -> Unit,
    onSkip: () -> Unit,
    onMigrate: () -> Unit,
    onCopy: () -> Unit,
    // KMK -->
    onCancel: () -> Unit,
    // KMK <--
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val closeMenu = { moreExpanded = false }
    Box(modifier) {
        when (result) {
            MigratingManga.SearchResult.Searching -> {
                // KMK -->
                IconButton(onClick = onCancel) {
                    // KMK <--
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(SYMR.strings.action_stop),
                    )
                }
            }
            MigratingManga.SearchResult.NotFound, is MigratingManga.SearchResult.Success -> {
                IconButton(onClick = { moreExpanded = !moreExpanded }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                    )
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(SYMR.strings.action_search_manually)) },
                        onClick = {
                            onSearchManually()
                            closeMenu()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(SYMR.strings.action_skip_entry)) },
                        onClick = {
                            onSkip()
                            closeMenu()
                        },
                    )
                    if (result is MigratingManga.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text(stringResource(SYMR.strings.action_migrate_now)) },
                            onClick = {
                                onMigrate()
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(SYMR.strings.action_copy_now)) },
                            onClick = {
                                onCopy()
                                closeMenu()
                            },
                        )
                    }
                }
            }
        }
    }
}
