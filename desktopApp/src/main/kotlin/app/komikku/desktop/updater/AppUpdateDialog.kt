package app.komikku.desktop.updater

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.komikku.desktop.platform.DesktopBrowser
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: shows an update found by [DesktopAppUpdateChecker] (start-up, scheduled or manual check) over the window: Android's
 * `NewUpdateScreen` texts ("New version available!", "Latest: …", the release notes), "Open on GitHub" opens the release
 * page in the browser (no in-app download / install on desktop), "Not now" dismisses it until the next check finds it.
 * When the browser cannot be opened (no AWT browse support, or it failed), the dialog stays and shows the release page
 * address as selectable text instead. An offered `.rpm` ([RpmInstall]) adds whether it is signed, the commands that
 * install it and a link to the release notes' Linux section.
 */
@Composable
fun AppUpdateDialogHost(
    checker: DesktopAppUpdateChecker = remember { Injekt.get() },
    openUrl: (String) -> Boolean = DesktopBrowser::open,
) {
    val release by checker.available.collectAsState()
    release?.let {
        // Per release: a newer one found later starts without the fallback
        var browserFailed by remember(it) { mutableStateOf(false) }
        AppUpdateDialog(
            release = it,
            browserFailed = browserFailed,
            onOpen = {
                if (openUrl(it.releaseLink)) checker.dismiss() else browserFailed = true
            },
            onDismiss = checker::dismiss,
            assetNames = checker.releaseAssets.namesOf(it),
            openUrl = openUrl,
        )
    }
}

@Composable
fun AppUpdateDialog(
    release: Release,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    browserFailed: Boolean = false,
    /** The release's file names ([DesktopReleaseAssets]): whether an offered `.rpm` is signed. */
    assetNames: List<String> = emptyList(),
    openUrl: (String) -> Boolean = DesktopBrowser::open,
) {
    val rpm = remember(release, assetNames) { RpmInstall.of(release, assetNames) }
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
                rpm?.let { RpmInstallNote(it, openUrl) }
                if (browserFailed) {
                    Text(
                        text = stringResource(KMR.strings.desktop_browser_open_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    SelectionContainer {
                        Text(
                            text = release.releaseLink,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp).testTag("app-update-release-link"),
                        )
                    }
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

@Composable
private fun RpmInstallNote(rpm: RpmInstall, openUrl: (String) -> Boolean) {
    var browserFailed by remember(rpm) { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 12.dp).testTag("app-update-rpm")) {
        Text(
            text = stringResource(if (rpm.signingKey == null) KMR.strings.desktop_update_rpm_unsigned else KMR.strings.desktop_update_rpm_signed),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer {
            Text(
                text = rpm.commands.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                // A command broken over two lines is easy to copy wrong
                softWrap = false,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState())
                    .testTag("app-update-rpm-commands"),
            )
        }
        TextButton(
            onClick = { browserFailed = !openUrl(rpm.instructionsUrl) },
            modifier = Modifier.testTag("app-update-rpm-instructions"),
        ) {
            Text(stringResource(KMR.strings.desktop_update_install_instructions))
        }
        if (browserFailed) {
            SelectionContainer {
                Text(
                    text = rpm.instructionsUrl,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("app-update-rpm-instructions-link"),
                )
            }
        }
    }
}
