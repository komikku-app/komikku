package app.komikku.desktop.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Android `ChapterDownloadAction` (presentation/manga/components, not borrowed: it uses `R` drawables). */
enum class ChapterDownloadAction { START, START_NOW, CANCEL, DELETE }

/**
 * Per-chapter download state like Android's `ChapterDownloadIndicator`: arrow (download), spinner with
 * progress and a menu (start now / cancel) while queued or downloading, check mark (menu: delete), error
 * (click: retry).
 */
@Composable
fun ChapterDownloadIndicator(
    chapterId: Long,
    download: MangaDetailsScreenModel.ChapterDownload,
    onAction: (ChapterDownloadAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                when (download.state) {
                    Download.State.NOT_DOWNLOADED -> onAction(ChapterDownloadAction.START)
                    Download.State.ERROR -> onAction(ChapterDownloadAction.START)
                    else -> menu = true
                }
            },
            modifier = Modifier.testTag("chapter-download-$chapterId-${download.state.name}"),
        ) {
            when (download.state) {
                Download.State.NOT_DOWNLOADED -> Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = stringResource(MR.strings.manga_download),
                )
                Download.State.QUEUE -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Download.State.DOWNLOADING -> if (download.progress > 0) {
                    CircularProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Download.State.DOWNLOADED -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(MR.strings.label_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Download.State.ERROR -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = stringResource(MR.strings.chapter_error),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (download.state == Download.State.DOWNLOADED) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_delete)) },
                    onClick = {
                        menu = false
                        onAction(ChapterDownloadAction.DELETE)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_start_downloading_now)) },
                    onClick = {
                        menu = false
                        onAction(ChapterDownloadAction.START_NOW)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_cancel)) },
                    onClick = {
                        menu = false
                        onAction(ChapterDownloadAction.CANCEL)
                    },
                )
            }
        }
    }
}
