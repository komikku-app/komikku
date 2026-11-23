package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.komikku.shared.jssource.JsSourceItem
import app.komikku.shared.jssource.JsSourcesContent
import app.komikku.shared.jssource.JsSourcesScreenModel
import app.komikku.shared.jssource.MangayomiSourceStore
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * JavaScript sources tab of the desktop browse screen: the shared list ([JsSourcesContent]) of repositories and
 * sources, with install / update / uninstall and each installed source's settings screen.
 */
@Composable
fun JsSourcesPane(model: JsSourcesScreenModel, store: MangayomiSourceStore) {
    val navigator = LocalNavigator.currentOrThrow
    val state by model.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = model::refresh, enabled = !state.isRefreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(KMR.strings.desktop_ext_refresh))
            }
        }
        JsSourcesContent(
            state = state,
            model = model,
            icon = { item -> JsSourceIcon(item, store) },
            onOpenSettings = { navigator.push(SourcePreferencesScreen(it)) },
        )
    }
}

@Composable
private fun JsSourceIcon(item: JsSourceItem, store: MangayomiSourceStore) {
    val modifier = Modifier.size(40.dp)
    val model: Any? = remember(item.installed?.updatedAt, item.offer?.descriptor?.iconUrl) {
        (if (item.installed != null) store.iconFileForSource(item.sourceId) else null)
            ?: item.descriptor.iconUrl.takeIf { it.startsWith("https://") }
    }
    if (model == null) {
        Icon(Icons.Outlined.Extension, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = model, contentDescription = null, modifier = modifier)
    }
}
