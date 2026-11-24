package app.komikku.desktop.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.manga.MangaInfoEdits
import eu.kanade.tachiyomi.util.lang.chop
import exh.util.trimOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

/**
 * Desktop counterpart of SY's `EditMangaDialog` (P10): title, author, artist, thumbnail URL, description, tags (remove,
 * add comma-separated), status, "Reset tags" and "Reset info"; the original values are the fields' labels. What it
 * starts with and what a save stores come from the shared [MangaInfoEdits] (Android's dialog is built from Android
 * views). Not on desktop: "Autofill from tracker" and the cover preview.
 */
@Composable
internal fun EditMangaInfoDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onSave: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    val initial = remember(manga) { MangaInfoEdits.initialFields(manga) }
    var title by remember(manga) { mutableStateOf(initial.title) }
    var author by remember(manga) { mutableStateOf(initial.author) }
    var artist by remember(manga) { mutableStateOf(initial.artist) }
    var thumbnailUrl by remember(manga) { mutableStateOf(initial.thumbnailUrl) }
    var description by remember(manga) { mutableStateOf(initial.description) }
    var tags by remember(manga) { mutableStateOf(initial.tags) }
    var statusIndex by remember(manga) { mutableStateOf(initial.statusIndex) }
    var newTags by remember(manga) { mutableStateOf("") }
    val local = manga.isLocal()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("edit-info-dialog"),
        title = { Text(stringResource(SYMR.strings.action_edit_info)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(520.dp).verticalScroll(rememberScrollState()),
            ) {
                InfoField(title, { title = it }, stringResource(SYMR.strings.title_hint, if (local) manga.url else manga.ogTitle), "edit-info-title")
                InfoField(author, { author = it }, stringResource(SYMR.strings.author_hint, if (local) "" else manga.ogAuthor.orEmpty()), "edit-info-author")
                InfoField(artist, { artist = it }, stringResource(SYMR.strings.artist_hint, if (local) "" else manga.ogArtist.orEmpty()), "edit-info-artist")
                InfoField(
                    thumbnailUrl,
                    { thumbnailUrl = it },
                    stringResource(SYMR.strings.thumbnail_url_hint, if (local) "" else manga.ogThumbnailUrl?.chop(40).orEmpty()),
                    "edit-info-thumbnail",
                )
                InfoField(
                    description,
                    { description = it },
                    stringResource(
                        SYMR.strings.description_hint,
                        if (local) "" else manga.ogDescription?.takeIf { it.isNotBlank() }?.replace("\n", " ")?.chop(20).orEmpty(),
                    ),
                    "edit-info-description",
                    singleLine = false,
                )
                StatusChoice(statusIndex) { statusIndex = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("edit-info-tags")) {
                    tags.forEachIndexed { index, tag ->
                        // A click removes the tag, like the close icon of Android's chips
                        InputChip(
                            selected = false,
                            onClick = { tags = tags.filterIndexed { i, _ -> i != index } },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_delete),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.testTag("edit-info-tag-$tag"),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTags,
                        onValueChange = { newTags = it },
                        label = { Text(stringResource(SYMR.strings.add_tags)) },
                        placeholder = { Text(stringResource(SYMR.strings.multi_tags_comma_separated)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit-info-new-tags"),
                    )
                    IconButton(
                        onClick = {
                            newTags.trimOrNull()?.let { input ->
                                tags = tags + input.split(",").mapNotNull { it.trimOrNull() }
                            }
                            newTags = ""
                        },
                        modifier = Modifier.testTag("edit-info-add-tags"),
                    ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(SYMR.strings.add_tags)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tags = MangaInfoEdits.resetTags(manga) }, modifier = Modifier.testTag("edit-info-reset-tags")) {
                        Text(stringResource(SYMR.strings.reset_tags))
                    }
                    OutlinedButton(
                        onClick = {
                            title = ""
                            author = ""
                            artist = ""
                            thumbnailUrl = ""
                            description = ""
                            tags = MangaInfoEdits.resetTags(manga)
                        },
                        modifier = Modifier.testTag("edit-info-reset-info"),
                    ) { Text(stringResource(SYMR.strings.reset_info)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, author, artist, thumbnailUrl, description, tags, MangaInfoEdits.statusAt(statusIndex))
                },
                modifier = Modifier.testTag("edit-info-save"),
            ) { Text(stringResource(MR.strings.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun InfoField(value: String, onValueChange: (String) -> Unit, label: String, tag: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun StatusChoice(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        MR.strings.label_default,
        MR.strings.ongoing,
        MR.strings.completed,
        MR.strings.licensed,
        MR.strings.publishing_finished,
        MR.strings.cancelled,
        MR.strings.on_hiatus,
    )
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(MR.strings.status))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("edit-info-status")) {
                Text(stringResource(labels[selected.coerceIn(labels.indices)]))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                        modifier = Modifier.testTag("edit-info-status-$index"),
                    )
                }
            }
        }
    }
}
