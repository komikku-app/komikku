package app.komikku.desktop.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.platform.DesktopBrowser
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Desktop tracking sheet (Android `TrackInfoDialogHomeScreen`) as a dialog over the manga details:
 * per logged-in tracker either "Add tracking" (search → pick) or the bound entry with status, chapter,
 * score, start / finish dates (when the tracker supports them), open in browser and remove.
 */
@Composable
fun TrackingDialog(model: TrackingScreenModel, mangaTitle: String, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    LaunchedEffect(Unit) { model.refresh() }

    val search = state.search
    if (search != null) {
        TrackSearchDialog(search, model)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(720.dp).testTag("tracking-dialog"),
        title = { Text(stringResource(MR.strings.manga_tracking_tab)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp)) {
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn {
                    items(state.items, key = { it.tracker.id }) { item ->
                        TrackerRow(item, model, mangaTitle)
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_close)) } },
    )
}

@Composable
private fun TrackerRow(item: TrackingScreenModel.Item, model: TrackingScreenModel, mangaTitle: String) {
    val tracker = item.tracker
    val track = item.track
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("tracker-${tracker.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tracker.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (track == null) {
                OutlinedButton(onClick = { model.startSearch(tracker, mangaTitle) }, modifier = Modifier.testTag("tracker-add-${tracker.id}")) {
                    Text(stringResource(MR.strings.add_tracking))
                }
            } else {
                if (track.remoteUrl.isNotBlank()) {
                    TextButton(onClick = { DesktopBrowser.open(track.remoteUrl) }) { Text(stringResource(MR.strings.action_open_in_browser)) }
                }
                TextButton(onClick = { model.remove(item) }, modifier = Modifier.testTag("tracker-remove-${tracker.id}")) {
                    Text(stringResource(MR.strings.action_remove))
                }
            }
        }
        if (track == null) return@Column
        Text(track.title, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(
                label = stringResource(MR.strings.track_status),
                value = tracker.getStatus(track.status)?.let { stringResource(it) } ?: track.status.toString(),
                options = tracker.getStatusList().map { status -> status to (tracker.getStatus(status)?.let { stringResource(it) } ?: status.toString()) },
                tag = "tracker-status-${tracker.id}",
                onSelect = { model.setStatus(item, it) },
            )
            Choice(
                label = stringResource(MR.strings.score),
                value = tracker.displayScore(track),
                options = tracker.getScoreList().map { it to it },
                tag = "tracker-score-${tracker.id}",
                onSelect = { model.setScore(item, it) },
            )
            NumberField(
                label = stringResource(MR.strings.chapters),
                value = track.lastChapterRead.toInt().toString() + if (track.totalChapters > 0) " / ${track.totalChapters}" else "",
                tag = "tracker-chapter-${tracker.id}",
                onSubmit = { it.toIntOrNull()?.let { chapter -> model.setChapter(item, chapter) } },
            )
            if (tracker.supportsReadingDates) {
                DateField(stringResource(MR.strings.track_started_reading_date), track.startDate, "tracker-start-${tracker.id}") {
                    model.setStartDate(item, it)
                }
                DateField(stringResource(MR.strings.track_finished_reading_date), track.finishDate, "tracker-finish-${tracker.id}") {
                    model.setFinishDate(item, it)
                }
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, value: String, options: List<Pair<T, String>>, tag: String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) { Text("$label: $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag("$tag-$text"),
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, tag: String, onSubmit: (String) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    val text = editing
    if (text == null) {
        OutlinedButton(onClick = { editing = value.substringBefore(" ") }, modifier = Modifier.testTag(tag)) { Text("$label: $value") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> editing = new.filter(Char::isDigit) },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.width(140.dp).testTag("$tag-input"),
            )
            TextButton(
                onClick = {
                    onSubmit(text)
                    editing = null
                },
                modifier = Modifier.testTag("$tag-ok"),
            ) { Text(stringResource(MR.strings.action_ok)) }
        }
    }
}

@Composable
private fun DateField(label: String, epochMillis: Long, tag: String, onSubmit: (LocalDate?) -> Unit) {
    val current = if (epochMillis > 0) Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString() else "-"
    var editing by remember { mutableStateOf<String?>(null) }
    val text = editing
    if (text == null) {
        OutlinedButton(onClick = { editing = if (epochMillis > 0) current else "" }, modifier = Modifier.testTag(tag)) { Text("$label: $current") }
    } else {
        val parsed = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { editing = it },
                label = { Text(label) },
                placeholder = { Text(stringResource(KMR.strings.desktop_date_hint)) },
                isError = text.isNotBlank() && parsed == null,
                singleLine = true,
                modifier = Modifier.width(180.dp).testTag("$tag-input"),
            )
            TextButton(
                onClick = {
                    onSubmit(parsed)
                    editing = null
                },
                enabled = text.isBlank() || parsed != null,
                modifier = Modifier.testTag("$tag-ok"),
            ) { Text(stringResource(MR.strings.action_ok)) }
        }
    }
}

@Composable
private fun TrackSearchDialog(search: TrackingScreenModel.Search, model: TrackingScreenModel) {
    var query by remember(search.tracker) { mutableStateOf(search.query) }
    AlertDialog(
        onDismissRequest = model::closeSearch,
        modifier = Modifier.width(720.dp).testTag("track-search"),
        title = { Text(search.tracker.name) },
        text = {
            Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("track-search-query"),
                    )
                    TextButton(onClick = { model.search(query) }, modifier = Modifier.testTag("track-search-go")) {
                        Text(stringResource(MR.strings.action_search))
                    }
                }
                when {
                    search.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    search.error != null -> Text(search.error, color = MaterialTheme.colorScheme.error)
                    search.results?.isEmpty() == true -> Text(stringResource(MR.strings.no_results_found))
                    else -> LazyColumn {
                        items(search.results.orEmpty(), key = { it.remote_id }) { result ->
                            ListItem(
                                modifier = Modifier.clickable { model.register(result) }.testTag("track-result-${result.remote_id}"),
                                headlineContent = { Text(result.title) },
                                supportingContent = {
                                    Text(
                                        listOf(result.publishing_type, result.start_date, result.publishing_status)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" • ") + if (result.summary.isNotBlank()) "\n" + result.summary.take(200) else "",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = model::closeSearch) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
