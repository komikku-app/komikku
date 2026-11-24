package eu.kanade.presentation.more.storage

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

@Composable
internal fun StorageSwitchDialog(
    switchToAutomatic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (switchToAutomatic) {
                        KMR.strings.settings_storage_switch_automatic_title
                    } else {
                        KMR.strings.settings_storage_switch_manual_title
                    },
                ),
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (switchToAutomatic) {
                        KMR.strings.settings_storage_switch_automatic_desc
                    } else {
                        KMR.strings.settings_storage_switch_manual_desc
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(KMR.strings.settings_storage_switch_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

// KMK <--
