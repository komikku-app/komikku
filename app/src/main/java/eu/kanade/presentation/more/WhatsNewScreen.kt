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
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
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
            MarkdownRender(
                content = changelogInfo.trimIndent(),
                flavour = GFMFlavourDescriptor(),
            )

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
                ## v1.13.1


                #### What's Changed
                ##### Fix

                - Fix mark existing duplicate read chapters as read option not working in some cases ([@AntsyLich](https://github.com/AntsyLich))
                - Fix: NaN when dragging `Start/Resume` reading button in MangaScreen ([@cuong-tran](https://github.com/cuong-tran))


                **Full Changelog**: [komikku-app/komikku@v1.13.0...v1.13.1](https://github.com/komikku-app/komikku/compare/v1.13.0...v1.13.1)


                ---
                ## v1.12.6


                #### What's Changed
                ##### Fix
                - bump version ([@cuong-tran](https://github.com/cuong-tran))
                - rename repo ([@cuong-tran](https://github.com/cuong-tran))

                **Full Changelog**: [komikku-app/komikku@v1.12.5...v1.12.6](https://github.com/komikku-app/komikku/compare/v1.12.5...v1.12.6)


                ---
                ## v1.12.5



                #### What's Changed
                ##### Fix

                - Fix (MangasPage): crash when extensions trying to destructuring MangasPage ([@cuong-tran](https://github.com/cuong-tran))

                **Full Changelog**: [komikku-app/komikku@v1.12.4...v1.12.5](https://github.com/komikku-app/komikku/compare/v1.12.4...v1.12.5)
            """,
            onOpenInBrowser = {},
            onAcceptUpdate = {},
        )
    }
}
