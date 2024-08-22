package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource

@Composable
fun SourceSettingsButton(
    id: Long,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    // Create a fake source
    val source = Source(id, "", "", supportsLatest = false, isStub = false)
    SourceSettingsButton(source = source)
}

@Composable
fun SourceSettingsButton(
    source: Source,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    // Avoid E-Hentai & ExHentai which is built-in & not actually installed extensions
    if (source.id == LocalSource.ID || source.id == EH_SOURCE_ID || source.id == EXH_SOURCE_ID) return

    val navigator = LocalNavigator.currentOrThrow
    IconButton(onClick = {
        if (source.installedExtension !== null) {
            navigator.push(ExtensionDetailsScreen(source.installedExtension!!.pkgName))
        }
    }) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(MR.strings.label_settings),
        )
    }
}
