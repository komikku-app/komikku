package app.komikku.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.extensions.ExtensionIconDrawable
import app.komikku.desktop.log.DesktopLogFile
import app.komikku.desktop.platform.DesktopBrowser
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Extensions tab: toolbar (refresh, extension stores), then updates pending / installed / untrusted /
 * available extensions with install, update, uninstall, trust and cancel actions and install progress.
 */
@Composable
fun ExtensionsPane(model: ExtensionsScreenModel) {
    val state by model.state.collectAsState()
    var showStores by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showStores = true }) {
                Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(MR.strings.extensionStores))
            }
            OutlinedButton(onClick = model::refresh, enabled = !state.isRefreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(KMR.strings.desktop_ext_refresh))
            }
            if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        val extensions = state.extensions
        when {
            extensions == null -> LoadingScreen()
            state.stores.isEmpty() && extensions.installed.isEmpty() && extensions.updates.isEmpty() &&
                extensions.untrusted.isEmpty() -> EmptyScreen(stringRes = MR.strings.extensionStoresScreen_emptyLabel)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (extensions.updates.isNotEmpty()) {
                    item(key = "header-updates") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ListGroupHeader(text = stringResource(MR.strings.ext_updates_pending), modifier = Modifier.weight(1f))
                            TextButton(onClick = model::updateAll) { Text(stringResource(MR.strings.ext_update_all)) }
                        }
                    }
                    items(extensions.updates, key = { "update-${it.pkgName}" }) { extension ->
                        ExtensionRow(extension, state.steps[extension.pkgName], model)
                    }
                }
                if (extensions.installed.isNotEmpty()) {
                    item(key = "header-installed") { ListGroupHeader(text = stringResource(MR.strings.ext_installed)) }
                    items(extensions.installed, key = { "installed-${it.pkgName}" }) { extension ->
                        ExtensionRow(extension, state.steps[extension.pkgName], model)
                    }
                }
                if (extensions.untrusted.isNotEmpty()) {
                    item(key = "header-untrusted") { ListGroupHeader(text = stringResource(MR.strings.ext_untrusted)) }
                    items(extensions.untrusted, key = { "untrusted-${it.pkgName}" }) { extension ->
                        ExtensionRow(extension, state.steps[extension.pkgName], model)
                    }
                }
                if (extensions.available.isNotEmpty()) {
                    item(key = "header-available") { ListGroupHeader(text = stringResource(KMR.strings.desktop_ext_available)) }
                    items(extensions.available, key = { "available-${it.pkgName}-${it.signatureHash}" }) { extension ->
                        ExtensionRow(extension, state.steps[extension.pkgName], model)
                    }
                }
            }
        }
    }

    state.installFailure?.let { failure ->
        InstallFailureDialog(failure, onDismiss = model::dismissInstallFailure)
    }

    if (showStores) {
        ExtensionStoresDialog(
            stores = state.stores,
            onAdd = model::addStore,
            onRemove = model::removeStore,
            onDismiss = { showStores = false },
        )
    }
}

@Composable
private fun ExtensionRow(extension: Extension, step: InstallStep?, model: ExtensionsScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    var confirmTrust by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    val configurableSource = (extension as? Extension.Installed)?.sources?.firstOrNull { it is ConfigurableSource }

    ListItem(
        // P22: like Android, an installed extension opens its extension info
        modifier = if (extension is Extension.Installed) {
            Modifier.clickable { navigator.push(ExtensionDetailsScreen(extension.pkgName)) }.testTag("extension-${extension.pkgName}")
        } else {
            Modifier
        },
        leadingContent = { ExtensionIcon(extension) },
        headlineContent = { Text(extension.name) },
        supportingContent = {
            val details = buildList {
                add(extension.versionName)
                extension.lang?.takeIf { it.isNotEmpty() }?.let { add(languageName(it)) }
                extension.storeName?.let(::add)
                if (extension.isNsfw) add(stringResource(MR.strings.ext_nsfw_short))
                if (extension is Extension.Installed && extension.isObsolete) add(stringResource(MR.strings.ext_obsolete))
                if (extension is Extension.Installed && extension.isRedundant) add(stringResource(SYMR.strings.ext_redundant))
            }
            Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (step != null && !step.isCompleted()) {
                    Text(stepLabel(step), style = MaterialTheme.typography.labelMedium)
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    IconButton(onClick = { model.cancel(extension) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
                    }
                    return@Row
                }
                when (extension) {
                    is Extension.Available -> Button(onClick = { model.install(extension) }) { Text(stringResource(MR.strings.ext_install)) }
                    is Extension.Installed -> {
                        if (configurableSource != null) {
                            IconButton(onClick = { navigator.push(SourcePreferencesScreen(configurableSource.id)) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                            }
                        }
                        if (extension.hasUpdate) {
                            Button(onClick = { model.update(extension) }) { Text(stringResource(MR.strings.ext_update)) }
                        }
                        OutlinedButton(onClick = { confirmUninstall = true }) { Text(stringResource(MR.strings.ext_uninstall)) }
                    }
                    is Extension.Untrusted -> {
                        Button(onClick = { confirmTrust = true }) { Text(stringResource(MR.strings.ext_trust)) }
                        OutlinedButton(onClick = { confirmUninstall = true }) { Text(stringResource(MR.strings.ext_uninstall)) }
                    }
                }
            }
        },
    )

    // P22 review: the APK is deleted at once, so ask first (Android: the system's uninstall dialog)
    if (confirmUninstall) {
        ExtensionUninstallDialog(
            name = extension.name,
            onConfirm = {
                confirmUninstall = false
                model.uninstall(extension)
            },
            onDismiss = { confirmUninstall = false },
        )
    }

    if (confirmTrust && extension is Extension.Untrusted) {
        AlertDialog(
            onDismissRequest = { confirmTrust = false },
            title = { Text(stringResource(MR.strings.untrusted_extension)) },
            text = { Text(stringResource(MR.strings.untrusted_extension_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTrust = false
                        model.trust(extension)
                    },
                ) { Text(stringResource(MR.strings.ext_trust)) }
            },
            dismissButton = { TextButton(onClick = { confirmTrust = false }) { Text(stringResource(MR.strings.action_cancel)) } },
        )
    }
}

@Composable
private fun stepLabel(step: InstallStep): String = when (step) {
    InstallStep.Pending -> stringResource(MR.strings.ext_pending)
    InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
    InstallStep.Installing -> stringResource(MR.strings.ext_installing)
    else -> ""
}

@Composable
internal fun ExtensionIcon(extension: Extension) {
    val modifier = Modifier.size(40.dp)
    val model: Any? = when (extension) {
        is Extension.Installed -> (extension.icon as? ExtensionIconDrawable)?.bytes
        is Extension.Available -> extension.iconUrl
        is Extension.Untrusted -> null
    }
    if (model == null) {
        Icon(Icons.Outlined.Extension, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = model, contentDescription = null, modifier = modifier)
    }
}

/** Why an install failed (the cause's first line, selectable) and where the log with the details is. */
@Composable
private fun InstallFailureDialog(failure: ExtensionsScreenModel.InstallFailure, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logFolder = DesktopLogFile.current?.directory ?: DesktopLogFile.directoryFor(context.dataDir)
    val folderNotOpened = stringResource(KMR.strings.desktop_folder_not_opened, logFolder.absolutePath)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("install-failure"),
        title = { Text(stringResource(KMR.strings.desktop_ext_install_failed, failure.name)) },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(KMR.strings.desktop_ext_install_failed_log, logFolder.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_ok)) } },
        dismissButton = {
            TextButton(onClick = { if (!DesktopBrowser.openFolder(logFolder)) context.toast(folderNotOpened) }) {
                Text(stringResource(KMR.strings.desktop_open_log_folder))
            }
        },
    )
}

/** Saved extension stores: add by index URL, remove. */
@Composable
private fun ExtensionStoresDialog(
    stores: List<ExtensionStore>,
    onAdd: suspend (String) -> Boolean,
    onRemove: (ExtensionStore) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.extensionStores)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stores.isEmpty()) Text(stringResource(MR.strings.extensionStoresScreen_emptyLabel))
                stores.forEach { store ->
                    ListItem(
                        headlineContent = { Text(store.name) },
                        supportingContent = { Text(store.indexUrl, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(store) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
                            }
                        },
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        failed = false
                    },
                    label = { Text(stringResource(MR.strings.extensionStoresScreen_addStoreInput_inputLabel)) },
                    singleLine = true,
                    isError = failed,
                    supportingText = if (failed) {
                        { Text(stringResource(KMR.strings.desktop_ext_store_add_failed)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = url.isNotBlank() && !adding,
                        onClick = {
                            adding = true
                            scope.launch {
                                val added = onAdd(url)
                                adding = false
                                failed = !added
                                if (added) url = ""
                            }
                        },
                    ) { Text(stringResource(MR.strings.action_addExtensionStore)) }
                    if (adding) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(MR.strings.extensionStoresScreen_addStore_processing))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_ok)) } },
    )
}
