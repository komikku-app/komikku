package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.lang.toRelativeString
import exh.metadata.MetadataUtil
import exh.source.isEhBasedManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun ChapterListDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    onFillermark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val context = LocalContext.current
    val state = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val downloadQueueState by downloadManager.queueState.collectAsState()

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = chapters,
                key = { "chapter-list-${it.chapter.id}" },
            ) { chapterItem ->
                val activeDownload = downloadQueueState.find { it.chapter.id == chapterItem.chapter.id }
                val progress = activeDownload?.let {
                    downloadManager.progressFlow()
                        .filter { it.chapter.id == chapterItem.chapter.id }
                        .map { it.progress }
                        .collectAsState(0).value
                } ?: 0
                val downloaded = if (chapterItem.manga.isLocal()) {
                    true
                } else {
                    downloadManager.isChapterDownloaded(
                        chapterItem.chapter.name,
                        chapterItem.chapter.scanlator,
                        chapterItem.manga.ogTitle,
                        chapterItem.manga.source,
                    )
                }
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                MangaChapterListItem(
                    title = chapterItem.chapter.name,
                    date = chapterItem.chapter.dateUpload
                        .takeIf { it > 0L }
                        ?.let {
                            // SY -->
                            if (manga?.isEhBasedManga() == true) {
                                MetadataUtil.EX_DATE_FORMAT
                                    .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()))
                            } else {
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(it),
                                    ZoneId.systemDefault(),
                                ).toRelativeString(context, dateRelativeTime, chapterItem.dateFormat)
                            }
                            // SY <--
                        },
                    readProgress = null,
                    scanlator = chapterItem.chapter.scanlator,
                    sourceName = null,
                    read = chapterItem.chapter.read,
                    bookmark = chapterItem.chapter.bookmark,
                    fillermark = chapterItem.chapter.fillermark,
                    selected = false,
                    downloadIndicatorEnabled = false,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { progress },
                    chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleFillermark,
                    chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                    onLongClick = { /*TODO*/ },
                    onClick = { onClickChapter(chapterItem.chapter) },
                    onDownloadClick = null,
                    onChapterSwipe = { action ->
                        if (action == LibraryPreferences.ChapterSwipeAction.ToggleBookmark) {
                            onBookmark(chapterItem.chapter)
                        }
                        if (action == LibraryPreferences.ChapterSwipeAction.ToggleFillermark) {
                            onFillermark(chapterItem.chapter)
                        }
                    },
                )
            }
        }
    }
}
