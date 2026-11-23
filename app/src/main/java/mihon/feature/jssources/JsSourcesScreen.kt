package mihon.feature.jssources

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.komikku.shared.jssource.JsSourceItem
import app.komikku.shared.jssource.JsSourcesContent
import app.komikku.shared.jssource.JsSourcesScreenModel
import app.komikku.shared.jssource.MangayomiSourceStore
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: Mangayomi JavaScript sources (docs/kmp-desktop/PROGRESS.md, "I4"): repositories, then installed and available
 * sources with install / update / uninstall. Reached from the Extensions tab and the Browse settings.
 */
class JsSourcesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { JsSourcesScreenModel() }
        val state by model.state.collectAsState()
        val store = remember { Injekt.get<MangayomiSourceStore>() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    navigateUp = navigator::pop,
                    title = stringResource(KMR.strings.js_sources),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = model::refresh, enabled = !state.isRefreshing) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(MR.strings.action_webview_refresh),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            JsSourcesContent(
                state = state,
                model = model,
                icon = { item -> JsSourceIcon(item, store) },
                onOpenSettings = { navigator.push(SourcePreferencesScreen(it)) },
                contentPadding = paddingValues,
            )
        }
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
