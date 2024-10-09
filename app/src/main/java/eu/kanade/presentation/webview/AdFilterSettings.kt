package eu.kanade.presentation.webview

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.webview.AdblockWebviewModel.FilterDialog
import io.github.edsuns.adfilter.DownloadState
import io.github.edsuns.adfilter.Filter
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun AdFilterSettings(
    filters: PersistentMap<String, Filter>,
    isAdblockEnabled: Boolean,
    isUpdatingAll: Boolean,
    masterFiltersSwitch: (Boolean) -> Unit,
    filterSwitch: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    updateFilter: (String) -> Unit,
    cancelUpdateFilter: (String) -> Unit,
    addFilter: (String, String) -> Unit,
    renameFilter: (String, String) -> Unit,
    removeFilter: (String) -> Unit,
    copyUrl: (String) -> Unit,
    filterDialog: FilterDialog?,
    openFilterDialog: (FilterDialog) -> Unit,
    closeFilterDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppBar(
                title = "AdBlock Settings",
                navigateUp = onDismissRequest,
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = if (isUpdatingAll) {
                                    stringResource(MR.strings.action_cancel)
                                } else {
                                    stringResource(MR.strings.action_webview_refresh)
                                },
                                icon = if (isUpdatingAll) {
                                    Icons.Outlined.Close
                                } else {
                                    Icons.Outlined.Refresh
                                },
                                onClick = {
                                    filters.keys.forEach {
                                        if (isUpdatingAll) {
                                            cancelUpdateFilter(it)
                                        } else {
                                            updateFilter(it)
                                        }
                                    }
                                },
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_add),
                                icon = Icons.Outlined.Add,
                                onClick = {
                                    openFilterDialog(FilterDialog.AddFilter)
                                },
                            ),
                        ),
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues),
        ) {
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                checked = isAdblockEnabled,
                onCheckedChanged = {
                    masterFiltersSwitch(it)
                },
            )
            HorizontalDivider()

            if (isAdblockEnabled) {
                ScrollbarLazyColumn(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(top = MaterialTheme.padding.small)
                        .fillMaxSize(),
                ) {
                    items(
                        items = filters.values.toImmutableList(),
                        key = { "adblock-filters-${it.id}" },
                    ) { filter ->
                        FilterSwitchItem(
                            url = filter.url,
                            name = filter.name,
                            isEnabled = filter.isEnabled,
                            downloadState = filter.downloadState,
                            updateTime = relativeDateText(filter.updateTime),
                            filtersCount = filter.filtersCount,
                            filterSwitch = { enabled ->
                                filterSwitch(filter.id, enabled)
                            },
                            updateFilter = { updateFilter(filter.id) },
                            cancelUpdateFilter = { cancelUpdateFilter(filter.id) },
                            renameFilter = {
                                openFilterDialog(
                                    FilterDialog.RenameFilter(filter.id, filter.name),
                                )
                            },
                            removeFilter = { removeFilter(filter.id) },
                            copyUrl = { copyUrl(filter.id) },
                        )
                    }
                }
            }
        }

        when (filterDialog) {
            is FilterDialog.AddFilter ->
                FilterAddDialog(
                    onDismissRequest = closeFilterDialog,
                    onConfirm = { name, url ->
                        closeFilterDialog()
                        addFilter(name, url)
                    },
                )

            is FilterDialog.RenameFilter ->
                FilterRenameDialog(
                    oldName = filterDialog.oldName,
                    onDismissRequest = closeFilterDialog,
                    onConfirm = { name ->
                        closeFilterDialog()
                        renameFilter(filterDialog.id, name)
                    },
                )

            else -> { }
        }
    }
}

@Composable
private fun FilterSwitchItem(
    url: String,
    name: String,
    isEnabled: Boolean,
    downloadState: DownloadState,
    updateTime: String,
    filtersCount: Int,
    filterSwitch: (Boolean) -> Unit,
    updateFilter: () -> Unit,
    cancelUpdateFilter: () -> Unit,
    renameFilter: () -> Unit,
    removeFilter: () -> Unit,
    copyUrl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.extraSmall,
                vertical = MaterialTheme.padding.small,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showMenu by remember { mutableStateOf(false) }
        var offsetPosition by remember { mutableStateOf(Offset.Zero) }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = { showMenu = true })
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            offsetPosition = event.changes.first().position
                        }
                    }
                },
        ) {
            if (showMenu) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            offsetPosition.x.toInt(),
                            offsetPosition.y.toInt(),
                        )
                    },
                ) {
                    FilterDropdownMenu(
                        isRunning = downloadState.isRunning,
                        updateFilter = updateFilter,
                        cancelUpdateFilter = cancelUpdateFilter,
                        renameFilter = renameFilter,
                        removeFilter = removeFilter,
                        copyUrl = copyUrl,
                        onDismissRequest = { showMenu = false },
                    )
                }
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    repeatDelayMillis = 2_000,
                ),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (downloadState.isRunning) {
                        downloadState.toString()
                    } else {
                        updateTime
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
                Text(
                    text = filtersCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = {
                filterSwitch(it)
            },
            modifier = Modifier.padding(start = TrailingWidgetBuffer),
        )
    }
}

@Composable
private fun FilterDropdownMenu(
    isRunning: Boolean,
    updateFilter: () -> Unit,
    cancelUpdateFilter: () -> Unit,
    renameFilter: () -> Unit,
    removeFilter: () -> Unit,
    copyUrl: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
        offset = DpOffset.Zero,
    ) {
        if (isRunning) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onDismissRequest()
                    cancelUpdateFilter()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(text = "Update") },
                onClick = {
                    onDismissRequest()
                    updateFilter()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(text = "Rename") },
            onClick = {
                onDismissRequest()
                renameFilter()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.action_copy_to_clipboard)) },
            onClick = {
                onDismissRequest()
                copyUrl()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.action_delete)) },
            onClick = {
                onDismissRequest()
                removeFilter()
            },
        )
    }
}

@Composable
fun FilterAddDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = {
                    onConfirm(name.trim(), url.trim())
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = "Add filter")
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(text = "URL")
                    },
                    supportingText = {
                        Text(text = stringResource(MR.strings.information_required_plain))
                    },
                    singleLine = true,
                )
            }
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun FilterRenameDialog(
    oldName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(oldName) }

    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim())
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_rename_category))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = "Rename filter")
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    singleLine = true,
                )
            }
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@PreviewLightDark
@Composable
private fun FilterSwitchPreview() {
    TachiyomiPreviewTheme {
        Surface {
            FilterSwitchItem(
                url = "https://filters.adtidy.org/extension/chromium/filters/118_optimized.txt",
                name = "AdGuard",
                isEnabled = true,
                downloadState = DownloadState.SUCCESS,
                updateTime = "Today",
                filtersCount = 1000,
                filterSwitch = { },
                updateFilter = { },
                cancelUpdateFilter = { },
                renameFilter = { },
                removeFilter = { },
                copyUrl = { },
            )
        }
    }
}
