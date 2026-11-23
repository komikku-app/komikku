package app.komikku.shared.jssource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.komikku.shared.jssource.JsSourcesScreenModel.Dialog
import app.komikku.sourcejs.repo.JsSourceStatus
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.Locale

/**
 * KMK: the JavaScript sources list shared by the Android screen and the desktop pane: repositories (add / remove),
 * then updates, installed and available sources with install / update / uninstall, plus the dialogs of [state].
 *
 * @param icon the icon of an item (platform image loading).
 * @param onOpenSettings opens the settings screen of an installed source (by Mihon source id).
 */
@Composable
fun JsSourcesContent(
    state: JsSourcesScreenModel.State,
    model: JsSourcesScreenModel,
    icon: @Composable (JsSourceItem) -> Unit,
    onOpenSettings: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val items = state.items
    if (items == null) {
        LoadingScreen(modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header-repos") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListGroupHeader(text = stringResource(KMR.strings.js_source_repos), modifier = Modifier.weight(1f))
                if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                TextButton(onClick = { model.showDialog(Dialog.AddRepository) }) {
                    Text(stringResource(KMR.strings.js_source_repo_add))
                }
            }
        }
        items(state.repositories, key = { "repo-${it.url}" }) { repository ->
            RepositoryRow(repository, onRemove = { model.showDialog(Dialog.RemoveRepository(repository)) })
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(KMR.strings.js_sources_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        section("updates", MR.strings.ext_updates_pending, state.updates, state, model, icon, onOpenSettings)
        section("installed", MR.strings.ext_installed, state.installed, state, model, icon, onOpenSettings)
        section("available", KMR.strings.js_source_available, state.available, state, model, icon, onOpenSettings)
    }

    JsSourcesDialogs(state, model)
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: dev.icerock.moko.resources.StringResource,
    items: List<JsSourceItem>,
    state: JsSourcesScreenModel.State,
    model: JsSourcesScreenModel,
    icon: @Composable (JsSourceItem) -> Unit,
    onOpenSettings: (Long) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$key") { ListGroupHeader(text = stringResource(title)) }
    items(items, key = { "$key-${it.id}" }) { item ->
        SourceRow(item, busy = item.id in state.busy, model = model, icon = icon, onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun RepositoryRow(repository: JsSourceRepository, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(repository.name ?: repository.url) },
        supportingContent = {
            val status = when {
                repository.error != null -> stringResource(KMR.strings.js_source_repo_fetch_failed, repository.error.text())
                repository.entries != null -> stringResource(KMR.strings.js_source_repo_entries, repository.entries)
                else -> stringResource(KMR.strings.js_source_repo_not_read)
            }
            Column {
                if (repository.name != null) Text(repository.url, style = MaterialTheme.typography.bodySmall)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (repository.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(KMR.strings.js_source_repo_remove))
            }
        },
    )
}

@Composable
private fun SourceRow(
    item: JsSourceItem,
    busy: Boolean,
    model: JsSourcesScreenModel,
    icon: @Composable (JsSourceItem) -> Unit,
    onOpenSettings: (Long) -> Unit,
) {
    val descriptor = item.descriptor
    ListItem(
        modifier = Modifier.testTag("js-source-${item.id}"),
        leadingContent = { icon(item) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(descriptor.name)
                JsSourceLabel()
            }
        },
        supportingContent = {
            Column {
                val details = buildList {
                    add(item.installed?.descriptor?.version ?: descriptor.version)
                    descriptor.lang.takeIf { it.isNotBlank() }?.let { add(languageName(it)) }
                    if (descriptor.isNsfw) add(stringResource(MR.strings.ext_nsfw_short))
                    when (item.status) {
                        JsSourceStatus.TEXT_CHANGED -> add(stringResource(KMR.strings.js_source_status_text_changed))
                        JsSourceStatus.OBSOLETE -> add(stringResource(MR.strings.ext_obsolete))
                        JsSourceStatus.UPDATE_AVAILABLE -> item.offer?.let { add("→ ${it.descriptor.version}") }
                        else -> {}
                    }
                }
                Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                if (descriptor.notes.isNotBlank()) {
                    Text(descriptor.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.loadError?.let {
                    Text(
                        stringResource(KMR.strings.js_source_load_error, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    return@Row
                }
                if (item.installed == null) {
                    Button(onClick = { model.requestInstall(item) }) { Text(stringResource(MR.strings.ext_install)) }
                    return@Row
                }
                if (item.loadError == null) {
                    IconButton(onClick = { onOpenSettings(item.sourceId) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                    }
                }
                if (item.hasUpdate) {
                    Button(onClick = { model.requestInstall(item) }) { Text(stringResource(MR.strings.ext_update)) }
                }
                OutlinedButton(onClick = { model.showDialog(Dialog.ConfirmUninstall(item)) }) {
                    Text(stringResource(MR.strings.ext_uninstall))
                }
            }
        },
    )
}

/** The small "JS" badge of a JavaScript source. */
@Composable
fun JsSourceLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(KMR.strings.js_source_label),
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        fontWeight = FontWeight.Bold,
    )
}

/** The `notes` of a JavaScript source, shown above its catalogue (Mangayomi shows them as a banner). */
@Composable
fun JsSourceNotesBanner(notes: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun JsSourcesDialogs(state: JsSourcesScreenModel.State, model: JsSourcesScreenModel) {
    when (val dialog = state.dialog) {
        Dialog.AddRepository -> AddRepositoryDialog(model)
        is Dialog.RemoveRepository -> AlertDialog(
            onDismissRequest = model::dismissDialog,
            title = { Text(stringResource(KMR.strings.js_source_repo_remove)) },
            text = { Text(stringResource(KMR.strings.js_source_repo_remove_confirm, dialog.repository.name ?: dialog.repository.url)) },
            confirmButton = {
                TextButton(onClick = { model.removeRepository(dialog.repository) }) { Text(stringResource(MR.strings.action_delete)) }
            },
            dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
        )
        is Dialog.ConfirmInstall -> ConfirmInstallDialog(dialog.download, model)
        is Dialog.ConfirmUninstall -> AlertDialog(
            onDismissRequest = model::dismissDialog,
            title = { Text(stringResource(MR.strings.ext_uninstall)) },
            text = { Text(stringResource(KMR.strings.js_source_uninstall_confirm, dialog.item.descriptor.name)) },
            confirmButton = {
                TextButton(onClick = { model.uninstall(dialog.item) }) { Text(stringResource(MR.strings.ext_uninstall)) }
            },
            dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
        )
        null -> {}
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = model::dismissError,
            title = { Text(stringResource(KMR.strings.js_sources)) },
            text = { Text(error.text()) },
            confirmButton = { TextButton(onClick = model::dismissError) { Text(stringResource(MR.strings.action_ok)) } },
        )
    }
}

@Composable
private fun AddRepositoryDialog(model: JsSourcesScreenModel) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<JsSourceStoreException?>(null) }
    AlertDialog(
        onDismissRequest = model::dismissDialog,
        title = { Text(stringResource(KMR.strings.js_source_repo_add)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                },
                label = { Text(stringResource(KMR.strings.js_source_repo_url_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { e -> { Text(e.text()) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TAG_ADD_REPOSITORY_CONFIRM),
                enabled = url.isNotBlank() && !adding,
                onClick = {
                    adding = true
                    scope.launch {
                        error = model.addRepository(url)
                        adding = false
                    }
                },
            ) {
                if (adding) CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun ConfirmInstallDialog(download: JsSourceDownload, model: JsSourcesScreenModel) {
    val name = download.offer.descriptor.name
    AlertDialog(
        onDismissRequest = model::dismissDialog,
        title = {
            Text(
                stringResource(
                    if (download.isUpdate) KMR.strings.js_source_update_confirm_title else KMR.strings.js_source_install_confirm_title,
                    name,
                ),
            )
        },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(KMR.strings.js_source_install_warning))
                    val installed = download.installed
                    if (installed != null) {
                        Text(stringResource(KMR.strings.js_source_update_versions, installed.descriptor.version, download.offer.descriptor.version))
                    } else {
                        Text(stringResource(KMR.strings.js_source_version, download.offer.descriptor.version))
                    }
                    if (download.textChangedUnderSameVersion) {
                        Text(
                            stringResource(KMR.strings.js_source_text_changed),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(stringResource(KMR.strings.js_source_host, download.host), fontWeight = FontWeight.Bold)
                    Text(stringResource(KMR.strings.js_source_size, formatBytes(download.sizeBytes)))
                    Text(
                        stringResource(KMR.strings.js_source_sha256, download.sha256),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (installed != null && !installed.sha256.equals(download.sha256, ignoreCase = true)) {
                        Text(
                            stringResource(KMR.strings.js_source_previous_sha256, installed.sha256),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.confirmInstall(download) }, modifier = Modifier.testTag(TAG_INSTALL_CONFIRM)) {
                Text(stringResource(if (download.isUpdate) MR.strings.ext_update else MR.strings.ext_install))
            }
        },
        dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

/** Test tags of the dialogs' confirm buttons (their texts also appear in the list behind them). */
const val TAG_ADD_REPOSITORY_CONFIRM = "js-source-add-repository-confirm"
const val TAG_INSTALL_CONFIRM = "js-source-install-confirm"

/** The user-facing text of a store failure. */
@Composable
fun JsSourceStoreException.text(): String = when (reason) {
    JsSourceStoreException.Reason.INSECURE_URL -> stringResource(KMR.strings.js_source_error_insecure_url)
    JsSourceStoreException.Reason.DOWNLOAD_FAILED -> stringResource(KMR.strings.js_source_error_download_failed)
    JsSourceStoreException.Reason.TOO_LARGE -> stringResource(KMR.strings.js_source_error_too_large)
    JsSourceStoreException.Reason.INVALID_INDEX -> stringResource(KMR.strings.js_source_error_invalid_index)
    JsSourceStoreException.Reason.SHA_MISMATCH -> stringResource(KMR.strings.js_source_error_sha_mismatch)
    JsSourceStoreException.Reason.NOT_FOUND -> stringResource(KMR.strings.js_source_error_not_found)
    JsSourceStoreException.Reason.LOAD_FAILED -> stringResource(KMR.strings.js_source_error_load_failed)
    JsSourceStoreException.Reason.STORAGE_FAILED -> stringResource(KMR.strings.js_source_error_storage_failed)
    JsSourceStoreException.Reason.UNEXPECTED -> stringResource(KMR.strings.js_source_error_unexpected, message.orEmpty())
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
}

private fun languageName(lang: String): String {
    if (lang.equals("all", ignoreCase = true) || lang == "other") return lang
    val locale = Locale.forLanguageTag(lang.replace('_', '-'))
    return locale.getDisplayName(locale).ifBlank { lang }.replaceFirstChar { it.titlecase(locale) }
}
