@file:Suppress("PropertyName")

package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun WhatsNewDialog(
    onDismissRequest: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME)) },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        // KMK -->
        dismissButton = {
            TextButton(onClick = onOpenWhatsNew) {
                Text(text = stringResource(MR.strings.whats_new))
            }
        },
        text = { Text(text = AboutScreen.getVersionName(withBuildDate = true)) },
        // KMK <--
    )
}

@Preview
@Composable
fun WhatsNewDialogPreview() {
    WhatsNewDialog({})
}
