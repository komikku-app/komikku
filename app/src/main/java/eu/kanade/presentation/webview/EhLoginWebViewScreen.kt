package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EhLoginWebViewScreen(
    onUp: () -> Unit,
    onPageFinished: (view: WebView, url: String) -> Unit,
    onClickRecheckLoginStatus: (loadUrl: (String) -> Unit) -> Unit,
    onClickAlternateLoginPage: (loadUrl: (String) -> Unit) -> Unit,
    onClickSkipPageRestyling: (loadUrl: (String) -> Unit) -> Unit,
    onClickCustomIgneousCookie: () -> Unit,
) {
    val state = rememberWebViewState(
        url = "https://forums.e-hentai.org/index.php?act=Login",
    )
    val navigator = rememberWebViewNavigator()
    val loading by produceState(true) {
        CookieManager.getInstance().removeAllCookies {
            value = false
        }
    }

    Scaffold(
        topBar = {
            Box {
                AppBar(
                    title = "ExHentai login",
                    navigateUp = onUp,
                    navigationIcon = Icons.Outlined.Close,
                )
                when (val loadingState = state.loadingState) {
                    is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    is LoadingState.Loading -> {
                        val animatedProgress by animateFloatAsState(
                            (loadingState as? LoadingState.Loading)?.progress ?: 1f,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "webview_loading",
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        )
                    }
                    else -> {}
                }
            }
        },
    ) { contentPadding ->

        if (loading) {
            return@Scaffold
        }

        val webClient = remember {
            object : AccompanistWebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished(view, url ?: return)
                }
            }
        }
        var showAdvancedOptions by rememberSaveable {
            mutableStateOf(false)
        }

        Box(Modifier.padding(contentPadding)) {
            Box {
                WebView(
                    state = state,
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    onCreated = { webView ->
                        webView.setDefaultSettings()

                        // Debug mode (chrome://inspect/#devices)
                        if (BuildConfig.DEBUG &&
                            0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                        ) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                    },
                    client = webClient,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(onClick = onUp, Modifier.weight(0.5F)) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(onClick = { showAdvancedOptions = true }, Modifier.weight(0.5F)) {
                        Text(text = stringResource(MR.strings.pref_category_advanced))
                    }
                }
            }
            if (showAdvancedOptions) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xb5000000)),
                ) {
                    Dialog(onDismissRequest = { showAdvancedOptions = false }) {
                        fun loadUrl(url: String) {
                            state.content = WebContent.Url(url)
                        }
                        Column(Modifier.fillMaxWidth(0.8F)) {
                            Button(
                                onClick = {
                                    onClickRecheckLoginStatus(::loadUrl)
                                    showAdvancedOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(SYMR.strings.recheck_login_status))
                            }
                            Button(
                                onClick = {
                                    onClickAlternateLoginPage(::loadUrl)
                                    showAdvancedOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(SYMR.strings.alternative_login_page))
                            }
                            Button(
                                onClick = {
                                    onClickSkipPageRestyling(::loadUrl)
                                    showAdvancedOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(SYMR.strings.skip_page_restyling))
                            }
                            Button(
                                onClick = {
                                    onClickCustomIgneousCookie()
                                    showAdvancedOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(SYMR.strings.custom_igneous_cookie))
                            }
                            Button(onClick = { showAdvancedOptions = false }, Modifier.fillMaxWidth()) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
