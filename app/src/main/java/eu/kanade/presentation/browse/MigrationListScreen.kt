package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.MigrationActionIcon
import eu.kanade.presentation.browse.components.MigrationItem
import eu.kanade.presentation.browse.components.MigrationItemResult
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MigrationListScreen(
    items: ImmutableList<MigratingManga>,
    migrationDone: Boolean,
    finishedCount: Int,
    getManga: suspend (MigratingManga.SearchResult.Result) -> Manga?,
    getChapterInfo: suspend (MigratingManga.SearchResult.Result) -> MigratingManga.ChapterInfo,
    getSourceName: (Manga) -> String,
    onMigrationItemClick: (Manga) -> Unit,
    openMigrationDialog: (Boolean) -> Unit,
    skipManga: (Long) -> Unit,
    searchManually: (MigratingManga) -> Unit,
    migrateNow: (Long) -> Unit,
    copyNow: (Long) -> Unit,
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
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.copy),
                                icon = if (items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll,
                                onClick = { openMigrationDialog(true) },
                                enabled = migrationDone,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.migrate),
                                icon = if (items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll,
                                onClick = { openMigrationDialog(false) },
                                enabled = migrationDone,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(
            contentPadding = contentPadding + topSmallPaddingValues,
        ) {
            items(items, key = { "migration-list-${it.manga.id}" }) { migrationItem ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(horizontal = 16.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val result by migrationItem.searchResult.collectAsState()
                    MigrationItem(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        manga = migrationItem.manga,
                        sourcesString = migrationItem.sourcesString,
                        chapterInfo = migrationItem.chapterInfo,
                        onClick = { onMigrationItemClick(migrationItem.manga) },
                    )

                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(SYMR.strings.migrating_to),
                        modifier = Modifier.weight(0.2f),
                    )

                    MigrationItemResult(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        migrationItem = migrationItem,
                        result = result,
                        getManga = getManga,
                        getChapterInfo = getChapterInfo,
                        getSourceName = getSourceName,
                        onMigrationItemClick = onMigrationItemClick,
                    )

                    MigrationActionIcon(
                        modifier = Modifier
                            .weight(0.2f),
                        result = result,
                        skipManga = { skipManga(migrationItem.manga.id) },
                        searchManually = { searchManually(migrationItem) },
                        migrateNow = {
                            migrateNow(migrationItem.manga.id)
                        },
                        copyNow = {
                            copyNow(migrationItem.manga.id)
                        },
                    )
                }
            }
        }
    }
}
