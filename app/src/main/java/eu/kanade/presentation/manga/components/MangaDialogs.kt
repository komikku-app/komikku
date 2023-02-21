package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.R

@Composable
fun DeleteChaptersDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        title = {
            Text(text = stringResource(R.string.are_you_sure))
        },
        text = {
            Text(text = stringResource(R.string.confirm_delete_chapters))
        },
    )
}

// SY -->
@Composable
fun SelectScanlatorsDialog(
    onDismissRequest: () -> Unit,
    availableScanlators: List<String>,
    initialSelectedScanlators: List<String>,
    onSelectScanlators: (List<String>) -> Unit,
) {
    val selected = remember {
        initialSelectedScanlators.toMutableStateList()
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.select_scanlators)) },
        text = {
            LazyColumn {
                availableScanlators.forEach { current ->
                    item {
                        val isSelected = selected.contains(current)
                        val onSelectionChanged = {
                            when (!isSelected) {
                                true -> selected.add(current)
                                false -> selected.remove(current)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onSelectionChanged() },
                                )
                                .minimumInteractiveComponentSize()
                                .fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                            )
                            Text(
                                text = current,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }
                    }
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectScanlators(selected.toList())
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSelectScanlators(availableScanlators)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(R.string.action_reset))
            }
        },
    )
}
// SY <--
