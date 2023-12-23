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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import eu.kanade.tachiyomi.R
import exh.util.toAnnotatedString

@Composable
fun SyncFavoritesWarningDialog(
    onDismissRequest: () -> Unit,
    onAccept: () -> Unit,
) {
    val context = LocalContext.current
    val text = remember {
        HtmlCompat.fromHtml(
            context.getString(R.string.favorites_sync_notes_message),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toAnnotatedString()
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        title = {
            Text(stringResource(R.string.favorites_sync_notes))
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
