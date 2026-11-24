package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.ui.relativeTimeSpanString
import app.komikku.shared.library.LibraryUpdateProgress
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.navigation.AppNavigatorHost
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Day header label for a timestamp: "Today" / "Yesterday" / localized date (Android uses relative dates too). */
@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(MR.strings.relative_time_today)
        today.minusDays(1) -> pluralStringResource(MR.plurals.relative_time, 1, 1)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun Cover(cover: MangaCover) {
    AsyncImage(
        model = cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(4.dp)),
    )
}

/** Progress of a running / finished library update (shared runner), with cancel. */
@Composable
fun LibraryUpdateBanner(runner: LibraryUpdateRunner = remember { Injekt.get() }) {
    val progress by runner.progress.collectAsState()
    val p = progress ?: return
    // A finished update that refreshed nothing stays visible when it skipped what was asked for (e.g. selected entries of
    // SY's excluded sources), so it does not look like an empty run
    if (p.finished && p.failed.isEmpty() && p.newChapters == 0 && !(p.total == 0 && p.skipped > 0)) return
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag("library-update-progress")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.summary(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (!p.finished) {
                    IconButton(onClick = runner::cancel) { Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel)) }
                }
            }
            if (!p.finished) {
                if (p.total > 0) {
                    LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateProgress.summary(): String = buildString {
    append(stringResource(MR.strings.updating_library))
    append(" ($done / $total)")
    if (current.isNotEmpty()) append(" · " + current.joinToString())
    if (newChapters > 0) append(" · +$newChapters")
    if (failed.isNotEmpty()) append(" · ✕ ${failed.size}")
    if (skipped > 0) append(" · " + pluralStringResource(MR.plurals.migrationListScreen_migrateDialog_skipText, skipped, skipped))
    // Android SY never updates galleries of these sources (E-Hentai, Pururin, NHentai delegates)
    if (excluded > 0) append(" · " + stringResource(MR.strings.skipped_reason_not_always_update))
}

/** Updates destination: recent chapters grouped by day; opens the reader or the manga; library refresh. */
data object UpdatesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = stringResource(MR.strings.label_recent_updates), icon = rememberVectorPainter(Icons.Outlined.NewReleases))

    @Composable
    override fun Content() = AppNavigatorHost(screen = UpdatesScreen)
}

@OptIn(ExperimentalMaterial3Api::class)
data object UpdatesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val runner = remember { Injekt.get<LibraryUpdateRunner>() }
        val launcher = remember { Injekt.get<ReaderLauncher>() }
        val updatesFlow = remember {
            Injekt.get<GetUpdates>().subscribe(
                instant = Instant.now().minus(90, ChronoUnit.DAYS),
                unread = null,
                started = null,
                bookmarked = null,
                hideExcludedScanlators = true,
            )
        }
        val updates by updatesFlow.collectAsState(initial = null)
        val lastUpdated by remember { Injekt.get<LibraryPreferences>().lastUpdatedTimestamp() }.collectAsState()
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(MR.strings.label_recent_updates)) },
                actions = {
                    IconButton(onClick = { runner.start(LibraryUpdateRunner.Target.All) }, modifier = Modifier.testTag("update-library")) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(MR.strings.action_update_library))
                    }
                },
            )
            LibraryUpdateBanner(runner)
            val list = updates ?: return@Column
            if (list.isEmpty()) {
                EmptyScreen(stringRes = MR.strings.information_no_recent)
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize().testTag("updates-list")) {
                // Android's updatesLastUpdatedItem (P3: set by every library update, manual or scheduled)
                item(key = "last-updated") {
                    Text(
                        stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp).testTag("updates-last-updated"),
                    )
                }
                list.groupBy { it.dateFetch.toLocalDate() }.forEach { (day, items) ->
                    item(key = "day-$day") {
                        Text(dayLabel(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                    }
                    items(items, key = { "update-${it.chapterId}" }) { update ->
                        ListItem(
                            modifier = Modifier
                                .clickable { launcher.open(update.mangaId, update.chapterId) }
                                .alpha(if (update.read) 0.5f else 1f)
                                .testTag("update-${update.chapterId}"),
                            leadingContent = {
                                Column(Modifier.clickable { navigator.push(MangaDetailsScreen(update.mangaId)) }) { Cover(update.coverData) }
                            },
                            headlineContent = { Text(update.mangaTitle) },
                            supportingContent = {
                                Text(
                                    update.chapterName +
                                        if (!update.read && update.lastPageRead > 0) {
                                            " • " + stringResource(MR.strings.chapter_progress, update.lastPageRead + 1)
                                        } else {
                                            ""
                                        },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** History destination: last read chapters grouped by day; resume, open the manga, remove one / all. */
data object HistoryTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = stringResource(MR.strings.label_recent_manga), icon = rememberVectorPainter(Icons.Outlined.History))

    @Composable
    override fun Content() = AppNavigatorHost(screen = HistoryScreen)
}

@OptIn(ExperimentalMaterial3Api::class)
data object HistoryScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val launcher = remember { Injekt.get<ReaderLauncher>() }
        val removeHistory = remember { Injekt.get<RemoveHistory>() }
        val getNextChapters = remember { Injekt.get<GetNextChapters>() }
        val historyFlow = remember { Injekt.get<GetHistory>().subscribe("", null, null, null) }
        val history by historyFlow.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        var confirmClear by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(MR.strings.label_recent_manga)) },
                actions = {
                    IconButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("clear-history")) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(MR.strings.pref_clear_history))
                    }
                },
            )
            val list = history ?: return@Column
            if (list.isEmpty()) {
                EmptyScreen(stringRes = MR.strings.information_no_recent_manga)
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize().testTag("history-list")) {
                list.groupBy { it.readAt?.time?.toLocalDate() ?: LocalDate.EPOCH }.forEach { (day, items) ->
                    item(key = "day-$day") {
                        Text(dayLabel(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                    }
                    items(items, key = { "history-${it.id}" }) { entry ->
                        val time = entry.readAt?.let {
                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it.toInstant().atZone(ZoneId.systemDefault()))
                        }
                        ListItem(
                            modifier = Modifier
                                .clickable { navigator.push(MangaDetailsScreen(entry.mangaId)) }
                                .testTag("history-${entry.chapterId}"),
                            leadingContent = { Cover(entry.coverData) },
                            headlineContent = { Text(entry.title) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        entry.chapterNumber.takeIf { it >= 0 }?.let {
                                            stringResource(MR.strings.display_mode_chapter, if (it % 1 == 0.0) it.toLong().toString() else it.toString())
                                        },
                                        time,
                                    ).joinToString(" • "),
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { removeHistoryEntry(scope, removeHistory, entry.id) }, modifier = Modifier.testTag("history-remove-${entry.chapterId}")) {
                                        Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_remove))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                // Android `HistoryScreenModel.getNextChapterForManga`: the next chapter after the read one
                                                val next = withIOContext { getNextChapters.await(entry.mangaId, entry.chapterId, onlyUnread = false).firstOrNull() }
                                                if (next != null) launcher.open(entry.mangaId, next.id) else launcher.open(entry.mangaId, entry.chapterId)
                                            }
                                        },
                                        modifier = Modifier.testTag("history-resume-${entry.chapterId}"),
                                    ) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(MR.strings.action_resume))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(MR.strings.pref_clear_history)) },
                text = { Text(stringResource(MR.strings.clear_history_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClear = false
                            scope.launch { withIOContext { removeHistory.awaitAll() } }
                        },
                        modifier = Modifier.testTag("clear-history-confirm"),
                    ) { Text(stringResource(MR.strings.action_ok)) }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(MR.strings.action_cancel)) } },
            )
        }
    }
}

private fun removeHistoryEntry(scope: kotlinx.coroutines.CoroutineScope, removeHistory: RemoveHistory, historyId: Long) {
    scope.launch { withIOContext { removeHistory.await(listOf(historyId)) } }
}
