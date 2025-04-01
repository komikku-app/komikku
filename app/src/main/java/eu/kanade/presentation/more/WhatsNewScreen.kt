package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun WhatsNewScreen(
    currentVersion: String,
    versionName: String,
    changelogInfo: String,
    onOpenInBrowser: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = stringResource(MR.strings.whats_new),
        subtitleText = stringResource(SYMR.strings.latest_, versionName) +
            " - " + stringResource(KMR.strings.current_, currentVersion),
        acceptText = stringResource(MR.strings.action_ok),
        onAcceptClick = onAcceptUpdate,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
        ) {
            MarkdownRender(content = changelogInfo)

            TextButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(MR.strings.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun WhatsNewScreenPreview() {
    TachiyomiPreviewTheme {
        WhatsNewScreen(
            currentVersion = "v0.99.9",
            versionName = "v1.00.0",
            changelogInfo = """
                ## Yay
                Foobar

                ### More info
                [komikku-app/komikku@23d862d17...48fb4a2e6](https://github.com/komikku-app/komikku/compare/23d862d17...48fb4a2e6)
                - Hello ([@cuong-tran](@https://github.com/cuong-tran))
                - World
            """.trimIndent(),
            onOpenInBrowser = {},
            onAcceptUpdate = {},
        )
    }
}
