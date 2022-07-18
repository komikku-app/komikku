package eu.kanade.presentation.category.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.TextButton
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.delay

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    // SY -->
    title: String,
    extraMessage: String? = null,
    // SY <--
) {
    val (name, onNameChange) = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onCreate(name)
                onDismissRequest()
            },) {
                Text(text = stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            // SY -->
            Column {
                if (extraMessage != null) {
                    Text(extraMessage)
                }
                // SY <--
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = onNameChange,
                    label = {
                        Text(text = stringResource(R.string.name))
                    },
                )
                // SY -->
            }
            // SY <--
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    category: String,
) {
    val (name, onNameChange) = remember { mutableStateOf(category) }
    val focusRequester = remember { FocusRequester.Default }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onRename(name)
                onDismissRequest()
            },) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.action_rename_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = onNameChange,
                label = {
                    Text(text = stringResource(R.string.name))
                },
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    title: String,
    text: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            },) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            Text(text = text)
        },
    )
}
