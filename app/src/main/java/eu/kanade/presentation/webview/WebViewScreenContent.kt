package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.webview.components.AdFilterLogDialog
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.webview.AdFilterModel
import eu.kanade.tachiyomi.util.system.getHtml
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.FilterViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun WebViewScreenContent(
    onNavigateUp: () -> Unit,
    initialTitle: String?,
    url: String,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearCookies: (String) -> Unit,
    // KMK -->
    adFilter: AdFilter,
    adFilterViewModel: FilterViewModel,
    adFilterModel: AdFilterModel,
    modifier: Modifier = Modifier,
    // KMK <--
    headers: Map<String, String> = emptyMap(),
    onUrlChange: (String) -> Unit = {},
) {
    val state = rememberWebViewState(url = url, additionalHttpHeaders = headers)
    val navigator = rememberWebViewNavigator()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(url) }
    var showCloudflareHelp by remember { mutableStateOf(false) }

    // KMK -->
    val lifecycleOwner = LocalLifecycleOwner.current
    val blockedCount by adFilterModel.blockedCount.collectAsState()
    val isAdblockEnabled by adFilterViewModel.isEnabled.collectAsState()
    // KMK <--

    val webClient = remember {
        object : AccompanistWebViewClient() {
            // KMK -->
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val result = adFilter.shouldIntercept(view!!, request!!)
                adFilterModel.onShouldInterceptRequest(result)
                return result.resourceResponse
            }
            // KMK <--

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // KMK -->
                adFilterModel.onPageStarted(url)
                adFilter.performScript(view, url)
                // KMK <--
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                scope.launch {
                    val html = view.getHtml()
                    showCloudflareHelp = "window._cf_chl_opt" in html || "Ray ID is" in html
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                request?.let {
                    // Don't attempt to open blobs as webpages
                    if (it.url.toString().startsWith("blob:http")) {
                        return false
                    }

                    // Ignore intents urls
                    if (it.url.toString().startsWith("intent://")) {
                        return true
                    }

                    // Continue with request, but with custom headers
                    view?.loadUrl(it.url.toString(), headers)
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    }

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = state.pageTitle ?: initialTitle,
                        subtitle = "($blockedCount) $currentUrl",
                        navigateUp = onNavigateUp,
                        navigationIcon = Icons.Outlined.Close,
                        actions = {
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_back),
                                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                        onClick = {
                                            if (navigator.canGoBack) {
                                                navigator.navigateBack()
                                            }
                                        },
                                        enabled = navigator.canGoBack,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_forward),
                                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                        onClick = {
                                            if (navigator.canGoForward) {
                                                navigator.navigateForward()
                                            }
                                        },
                                        enabled = navigator.canGoForward,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_webview_refresh),
                                        onClick = { navigator.reload() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_share),
                                        onClick = { onShare(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        onClick = { onOpenInBrowser(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_clear_cookies),
                                        onClick = { onClearCookies(currentUrl) },
                                    ),
                                    // KMK -->
                                    AppBar.OverflowAction(
                                        title = "AdBlock ($blockedCount)",
                                        onClick = {
                                            if (isAdblockEnabled) {
                                                adFilterModel.showFilterLogDialog()
                                            } else {
                                                adFilterModel.showFilterSettingsDialog()
                                            }
                                        },
                                    ),
                                    // KMK <--
                                ),
                            )
                        },
                    )

                    if (showCloudflareHelp) {
                        Surface(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            WarningBanner(
                                textRes = MR.strings.information_cloudflare_help,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        uriHandler.openUri(
                                            "https://mihon.app/docs/guides/troubleshooting/#cloudflare",
                                        )
                                    },
                            )
                        }
                    }
                }
                when (val loadingState = state.loadingState) {
                    is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    is LoadingState.Loading -> LinearProgressIndicator(
                        progress = { (loadingState as? LoadingState.Loading)?.progress ?: 1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    else -> {}
                }
            }
        },
        modifier = modifier,
    ) { contentPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            navigator = navigator,
            onCreated = { webView ->
                webView.setDefaultSettings()

                // KMK -->
                // Setup AdblockAndroid for your WebView.
                adFilter.setupWebView(webView)

                // Add filter list subscriptions on first installation.
                if (!adFilter.hasInstallation) {
                    val map = mapOf(
                        "AdGuard Base" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
                        "EasyPrivacy Lite" to "https://filters.adtidy.org/extension/chromium/filters/118_optimized.txt",
                        "AdGuard Tracking Protection" to "https://filters.adtidy.org/extension/chromium/filters/3.txt",
                        "AdGuard Annoyances" to "https://filters.adtidy.org/extension/chromium/filters/14.txt",
                        "AdGuard Chinese" to "https://filters.adtidy.org/extension/chromium/filters/224.txt",
                        "NoCoin Filter List" to "https://filters.adtidy.org/extension/chromium/filters/242.txt",
                    )
                    for ((key, value) in map) {
                        val subscription = adFilterViewModel.addFilter(key, value)
                        // filterViewModel.download(key)
                        adFilterViewModel.download(subscription.id)
                    }
                }

                // Observe `blockingInfoMap` to update the blocking count
                scope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        adFilterModel.blockingInfoMap.collect {
                            adFilterModel.updateBlockedCount()
                        }
                    }
                }

                // Observe whenever AdFilter is enabled/disabled
                scope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        adFilterViewModel.isEnabled.collect {
                            adFilterModel.updateBlockedCount()
                        }
                    }
                }

                // Observe whenever filters' settings are changed via `onDirty` flag
                scope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        adFilterViewModel.onDirty.collect {
                            // Clear cache when there are changes to the filter.
                            // You need to refresh the page manually to make the changes take effect.
                            webView.clearCache(false)
                            adFilterModel.dirtyBlockingInfo = true
                            adFilterModel.updateBlockedCount()
                        }
                    }
                }
                // KMK <--

                // Debug mode (chrome://inspect/#devices)
                if (BuildConfig.DEBUG &&
                    0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                ) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                headers["user-agent"]?.let {
                    webView.settings.userAgentString = it
                }
            },
            client = webClient,
        )
    }

    val dialog by adFilterModel.dialog.collectAsState()
    dialog?.let {
        val onDismissRequest = adFilterModel::dismissDialog
        val filters by adFilterViewModel.filters.collectAsState()
        val blockingInfoMap by adFilterModel.blockingInfoMap.collectAsState()
        when (it) {
            is AdFilterModel.Dialog.FilterLogDialog -> {
                AdFilterLogDialog(
                    blockingInfo = blockingInfoMap[currentUrl],
                    onAdBlockSettingClick = {
                        adFilterModel.showFilterSettingsDialog(
                            onDismissDialog = {
                                adFilterModel.showFilterLogDialog()
                            },
                        )
                    },
                    onDismissRequest = onDismissRequest,
                )
            }

            is AdFilterModel.Dialog.FilterSettingsDialog -> {
                AdFilterSettings(
                    filters = filters,
                    isAdblockEnabled = isAdblockEnabled,
                    masterFiltersSwitch = adFilterViewModel::masterEnableDisable,
                    filterSwitch = adFilterViewModel::setFilterEnabled,
                    onDismissRequest = {
                        if (it.onDismissDialog != null) {
                            it.onDismissDialog.invoke()
                        } else {
                            onDismissRequest()
                        }
                    },
                )
            }
        }
    }
}
