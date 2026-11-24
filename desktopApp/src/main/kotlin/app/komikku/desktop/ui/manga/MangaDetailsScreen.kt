package app.komikku.desktop.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.ui.browse.CoverItem
import app.komikku.desktop.ui.browse.GlobalSearchScreen
import app.komikku.desktop.webview.WebViewDialogs
import app.komikku.desktop.webview.rememberWebViewLauncher
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import java.text.DateFormat
import java.util.Date

/**
 * Desktop manga details (Phase 6-lite): cover and info, library + categories, continue reading,
 * refresh from the source, sort, and the stored chapter list with read / bookmark / progress state,
 * single and multi selection actions. Opening a chapter starts the reader. The shared Android manga
 * screen replaces this in Phase 6.2.
 */
class MangaDetailsScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MangaDetailsScreenModel(mangaId) }
        val state by model.state.collectAsState()
        val manga = state.manga
        // P7: Android's "Open in WebView" (`MangaScreen.openMangaInWebView`)
        val webView = rememberWebViewLauncher()
        BrowseScaffold(
            title = manga?.title.orEmpty(),
            onBack = navigator::pop,
            actions = {
                // Resolved on click, off the UI thread: the source may load later and `getMangaUrl` is extension code.
                if (manga != null) {
                    IconButton(onClick = { webView.open(manga.source) { model.mangaUrl() } }, modifier = Modifier.testTag("details-webview")) {
                        Icon(Icons.Outlined.Public, contentDescription = stringResource(MR.strings.action_open_in_web_view))
                    }
                }
                IconButton(onClick = model::refresh, enabled = !state.isRefreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(KMR.strings.desktop_refresh))
                }
                if (manga != null && !manga.isLocal()) DownloadMenu(model::runDownloadAction)
                if (manga != null) SortMenu(manga, model::setSorting)
            },
        ) {
            if (manga == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (state.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("details-refreshing"))
                    if (state.selection.isNotEmpty()) SelectionBar(state, model)
                    DetailsContent(state, manga, model)
                }
            }
        }
        when (val dialog = state.dialog) {
            is MangaDetailsScreenModel.Dialog.ChangeCategories -> CategoryDialog(dialog, model)
            MangaDetailsScreenModel.Dialog.Tracking -> {
                val trackingModel = rememberScreenModel(tag = "tracking") { TrackingScreenModel(mangaId) }
                TrackingDialog(trackingModel, manga?.title.orEmpty(), onDismiss = model::dismissDialog)
            }
            null -> {}
        }
        WebViewDialogs(webView)
    }
}

@Composable
private fun SortMenu(manga: Manga, onSort: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(MR.strings.action_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                Manga.CHAPTER_SORTING_SOURCE to MR.strings.sort_by_source,
                Manga.CHAPTER_SORTING_NUMBER to MR.strings.sort_by_number,
                Manga.CHAPTER_SORTING_UPLOAD_DATE to MR.strings.sort_by_upload_date,
                Manga.CHAPTER_SORTING_ALPHABET to MR.strings.action_sort_alpha,
            ).forEach { (mode, label) ->
                val active = manga.sorting == mode
                DropdownMenuItem(
                    text = { Text(stringResource(label) + if (active) (if (manga.sortDescending()) "  ↓" else "  ↑") else "") },
                    onClick = {
                        expanded = false
                        onSort(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadMenu(onAction: (DownloadAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("details-download-menu")) {
            Icon(Icons.Outlined.Download, contentDescription = stringResource(MR.strings.manga_download))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                DownloadAction.NEXT_1_CHAPTER to pluralStringResource(MR.plurals.download_amount, 1, 1),
                DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 5, 5),
                DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 10, 10),
                DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 25, 25),
                DownloadAction.UNREAD_CHAPTERS to stringResource(MR.strings.download_unread),
                DownloadAction.BOOKMARKED_CHAPTERS to stringResource(MR.strings.download_bookmarked),
            ).forEach { (action, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onAction(action)
                    },
                    modifier = Modifier.testTag("download-action-${action.name}"),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(state: MangaDetailsScreenModel.State, model: MangaDetailsScreenModel) {
    val selected = state.selectedChapters
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = { model.selectAll(false) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
            }
            Text(selected.size.toString(), modifier = Modifier.weight(1f))
            TextButton(onClick = { model.selectAll(true) }) { Text(stringResource(MR.strings.action_select_all)) }
            IconButton(onClick = { model.markRead(selected, true) }) {
                Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(MR.strings.action_mark_as_read))
            }
            IconButton(onClick = { model.markRead(selected, false) }) {
                Icon(Icons.Outlined.RemoveDone, contentDescription = stringResource(MR.strings.action_mark_as_unread))
            }
            IconButton(onClick = { model.bookmark(selected, true) }) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = stringResource(MR.strings.action_bookmark))
            }
            IconButton(onClick = { model.bookmark(selected, false) }) {
                Icon(Icons.Outlined.BookmarkRemove, contentDescription = stringResource(MR.strings.action_remove_bookmark))
            }
            if (state.manga?.isLocal() == false) {
                IconButton(onClick = { model.download(selected) }, modifier = Modifier.testTag("selection-download")) {
                    Icon(Icons.Outlined.Download, contentDescription = stringResource(MR.strings.manga_download))
                }
                IconButton(onClick = { model.deleteDownloads(selected) }, modifier = Modifier.testTag("selection-delete")) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
                }
            }
            if (selected.size == 1) {
                TextButton(onClick = { model.markPreviousRead(selected.single()) }) {
                    Text(stringResource(MR.strings.action_mark_previous_as_read))
                }
            }
        }
    }
}

@Composable
private fun DetailsContent(state: MangaDetailsScreenModel.State, manga: Manga, model: MangaDetailsScreenModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("details-list"), contentPadding = PaddingValues(16.dp)) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.width(160.dp)) { CoverItem(manga = manga, onClick = {}) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    // Like Android's manga info header: clicking the title searches it in all sources
                    val navigator = LocalNavigator.currentOrThrow
                    Text(
                        manga.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .testTag("details-title")
                            .clickable(enabled = manga.title.isNotBlank()) { navigator.push(GlobalSearchScreen(manga.title)) },
                    )
                    Text(
                        listOfNotNull(manga.author, manga.artist?.takeIf { it != manga.author })
                            .joinToString().ifEmpty { stringResource(MR.strings.unknown_author) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (manga.favorite) {
                            Button(onClick = model::toggleFavorite) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(MR.strings.in_library))
                            }
                            OutlinedButton(onClick = model::showCategoryDialog) {
                                Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(MR.strings.action_move_category))
                            }
                        } else {
                            OutlinedButton(onClick = model::toggleFavorite) {
                                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(MR.strings.add_to_library))
                            }
                        }
                        OutlinedButton(onClick = model::showTrackingDialog, modifier = Modifier.testTag("details-tracking")) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(MR.strings.manga_tracking_tab))
                        }
                        state.nextChapter?.let { next ->
                            val resume = state.chapters.any { it.read || it.lastPageRead > 0 }
                            Button(onClick = model::continueReading, modifier = Modifier.testTag("details-continue")) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(if (resume) MR.strings.action_resume else MR.strings.action_start) + " · " + next.name)
                            }
                        }
                    }
                    manga.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    manga.genre?.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(key = "chapters-header") {
            Text(
                text = stringResource(MR.strings.chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )
        }
        state.error?.let { error ->
            item(key = "error") {
                Text(
                    text = if (error == MangaDetailsScreenModel.NO_CHAPTERS) {
                        stringResource(MR.strings.no_chapters_error)
                    } else {
                        stringResource(KMR.strings.desktop_source_error, error)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(state.chapters, key = { "chapter-${it.id}" }) { chapter ->
            ChapterRow(
                chapter = chapter,
                selected = chapter.id in state.selection,
                selectionMode = state.selection.isNotEmpty(),
                onClick = { if (state.selection.isNotEmpty()) model.toggleSelection(chapter) else model.openReader(chapter) },
                onToggleSelection = { model.toggleSelection(chapter) },
                download = if (manga.isLocal()) null else state.downloads[chapter.id] ?: NotDownloaded,
                onDownloadAction = { action ->
                    when (action) {
                        ChapterDownloadAction.START -> model.download(listOf(chapter))
                        ChapterDownloadAction.START_NOW -> model.startDownloadNow(chapter)
                        ChapterDownloadAction.CANCEL -> model.cancelDownload(chapter)
                        ChapterDownloadAction.DELETE -> model.deleteDownloads(listOf(chapter))
                    }
                },
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    download: MangaDetailsScreenModel.ChapterDownload?,
    onDownloadAction: (ChapterDownloadAction) -> Unit,
) {
    val details = buildList {
        if (chapter.dateUpload > 0) add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chapter.dateUpload)))
        if (!chapter.read && chapter.lastPageRead > 0) add(stringResource(MR.strings.chapter_progress, chapter.lastPageRead + 1))
        chapter.scanlator?.takeIf { it.isNotBlank() }?.let(::add)
    }
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .alpha(if (chapter.read && !selected) 0.5f else 1f)
            .testTag("chapter-${chapter.id}"),
        leadingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.alpha(if (selectionMode || selected) 1f else 0.4f).testTag("chapter-select-${chapter.id}"),
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chapter.bookmark) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = stringResource(MR.strings.action_bookmark),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(chapter.name)
            }
        },
        supportingContent = details.takeIf { it.isNotEmpty() }?.let { { Text(it.joinToString(" • ")) } },
        trailingContent = download?.let { { ChapterDownloadIndicator(chapter.id, it, onDownloadAction) } },
    )
}

@Composable
private fun CategoryDialog(dialog: MangaDetailsScreenModel.Dialog.ChangeCategories, model: MangaDetailsScreenModel) {
    var selected by remember(dialog) { mutableStateOf(dialog.selected) }
    AlertDialog(
        onDismissRequest = model::dismissDialog,
        title = { Text(stringResource(MR.strings.action_move_category)) },
        text = {
            Column {
                dialog.categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (category.id in selected) selected - category.id else selected + category.id
                        },
                    ) {
                        Checkbox(checked = category.id in selected, onCheckedChange = null)
                        Text(category.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.setCategoriesAndAddToLibrary(selected.toList()) }) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

private val NotDownloaded = MangaDetailsScreenModel.ChapterDownload(Download.State.NOT_DOWNLOADED)
