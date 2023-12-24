package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import exh.util.toAnnotatedString
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SyncFavoritesWarningDialog(
    onDismissRequest: () -> Unit,
    onAccept: () -> Unit,
) {
    val context = LocalContext.current
    val text = remember {
        HtmlCompat.fromHtml(
            context.stringResource(SYMR.strings.favorites_sync_notes_message),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        ).toAnnotatedString()
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.favorites_sync_notes))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = text)
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}
