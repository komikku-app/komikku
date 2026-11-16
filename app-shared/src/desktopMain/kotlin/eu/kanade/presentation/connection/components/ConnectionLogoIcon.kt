package eu.kanade.presentation.connection.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.track.components.InitialsLogo
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import tachiyomi.presentation.core.i18n.stringResource

/** KMK: desktop counterpart of `ConnectionLogoIcon` (no Android drawables): the service name's initial. */
@Composable
fun ConnectionLogoIcon(
    service: ConnectionsService,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    InitialsLogo(stringResource(service.nameStrRes()), modifier, onClick)
}
