package eu.kanade.tachiyomi.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.webview.WebViewScreenContent
import io.github.edsuns.adfilter.AdFilter

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {
    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { WebViewScreenModel(sourceId) }

        // KMK -->
        val adFilter = AdFilter.get(context)
        val adFilterViewModel = adFilter.viewModel
        val adblockWebviewModel = remember { AdblockWebviewModel(filterViewModel = adFilterViewModel) }
        // KMK <--

        WebViewScreenContent(
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = screenModel.headers,
            onUrlChange = { assistUrl = it },
            onShare = { screenModel.shareWebpage(context, it) },
            onOpenInBrowser = { screenModel.openInBrowser(context, it) },
            onClearCookies = screenModel::clearCookies,
            // KMK -->
            adFilter = adFilter,
            adFilterViewModel = adFilterViewModel,
            adblockWebviewModel = adblockWebviewModel,
            // KMK <--
        )
    }
}
