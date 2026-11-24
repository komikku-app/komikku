package app.komikku.desktop.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop counterpart of Android's `DuplicateMangaDialog` (P10): the entry being added, the library entries that look
 * like it (cover, title, chapter count, source) and "Add anyway" / "Cancel". Clicking a duplicate opens it (on
 * Android a click migrates and a long press opens; migration is not on desktop). Android's composable is not borrowed:
 * it is built on the Android cover component (drawable resources), its adaptive sheet and the preference widgets'
 * sizing; the detection itself is the shared `GetDuplicateLibraryManga`.
 *
 * P14: for bulk favourites (several selected entries, Android's `bulkFavoriteManga` mode) also "Allow all" / "Skip all"
 * / "Skip it" ([onAllowAllDuplicate], [onSkipAllDuplicate], [onSkipDuplicate]; each closes the dialog first).
 */
@Composable
internal fun DuplicateMangaDialog(
    targetManga: Manga,
    duplicates: List<MangaWithChapterCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: (Manga) -> Unit,
    onAllowAllDuplicate: (() -> Unit)? = null,
    onSkipAllDuplicate: (() -> Unit)? = null,
    onSkipDuplicate: (() -> Unit)? = null,
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("duplicate-dialog"),
        title = { Text(stringResource(MR.strings.possible_duplicates_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(targetManga.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(MR.strings.possible_duplicates_summary), style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(duplicates, key = { it.manga.id }) { duplicate ->
                        val manga = duplicate.manga
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenManga(manga) }
                                .padding(4.dp)
                                .testTag("duplicate-${manga.id}"),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            CoverImage(
                                model = manga.asMangaCover(),
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                            )
                            Text(manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(MR.plurals.manga_num_chapters, duplicate.chapterCount.toInt(), duplicate.chapterCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                sourceManager.getOrStub(manga.source).name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
                modifier = Modifier.testTag("duplicate-add-anyway"),
            ) { Text(stringResource(MR.strings.action_add_anyway)) }
        },
        dismissButton = {
            Row {
                listOfNotNull(
                    onSkipDuplicate?.let { Triple(it, KMR.strings.action_skip_duplicate_manga, "duplicate-skip") },
                    onSkipAllDuplicate?.let { Triple(it, KMR.strings.action_skip_all_duplicate_manga, "duplicate-skip-all") },
                    onAllowAllDuplicate?.let { Triple(it, KMR.strings.action_allow_all_duplicate_manga, "duplicate-allow-all") },
                ).forEach { (action, label, tag) ->
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            action()
                        },
                        modifier = Modifier.testTag(tag),
                    ) { Text(stringResource(label)) }
                }
                TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
            }
        },
    )
}
