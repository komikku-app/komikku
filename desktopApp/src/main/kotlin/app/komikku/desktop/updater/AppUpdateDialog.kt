package app.komikku.desktop.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.platform.DesktopBrowser
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: shows an update found by [DesktopAppUpdateChecker] (start-up, scheduled or manual check) over the window: Android's
 * `NewUpdateScreen` texts ("New version available!", "Latest: …", the release notes), "Open on GitHub" opens the release
 * page in the browser (no in-app download / install on desktop), "Not now" dismisses it until the next check finds it.
 */
@Composable
fun AppUpdateDialogHost(
    checker: DesktopAppUpdateChecker = remember { Injekt.get() },
    openUrl: (String) -> Boolean = DesktopBrowser::open,
) {
    val release by checker.available.collectAsState()
    release?.let {
        AppUpdateDialog(
            release = it,
            onOpen = {
                openUrl(it.releaseLink)
                checker.dismiss()
            },
            onDismiss = checker::dismiss,
        )
    }
}

@Composable
fun AppUpdateDialog(release: Release, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("app-update-dialog"),
        title = { Text(stringResource(MR.strings.update_check_notification_update_available)) },
        text = {
            Column {
                Text(stringResource(SYMR.strings.latest_, release.version), style = MaterialTheme.typography.titleSmall)
                // Plain text: the desktop has no Markdown renderer (Android: `MarkdownRender`). `getLatest` joins the
                // release notes with `\r`.
                SelectionContainer {
                    Text(
                        text = release.info.replace("\r\n", "\n").replace('\r', '\n').trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("app-update-notes"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen, modifier = Modifier.testTag("app-update-open")) {
                Text(stringResource(MR.strings.update_check_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("app-update-dismiss")) {
                Text(stringResource(MR.strings.action_not_now))
            }
        },
    )
}
