package exh.md.utils

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS = 10L

/**
 * Evaluates [script] on a blank page served as [baseUrl], so it can use that site's localStorage as shared with the
 * in-app WebView, e.g. to read a login made there.
 *
 * @return the script result if it's a string, otherwise `null`, also when the page didn't load in time.
 */
@SuppressLint("SetJavaScriptEnabled")
suspend fun evaluateOnSite(baseUrl: String, script: String): String? {
    val context = Injekt.get<Application>()
    val executor = ContextCompat.getMainExecutor(context)
    val latch = CountDownLatch(1)
    var webView: WebView? = null
    var result: String? = null

    executor.execute {
        val view = WebView(context)
        webView = view

        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(script) { value ->
                    result = value
                    latch.countDown()
                }
            }
        }

        view.loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
    }

    withContext(Dispatchers.IO) { latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

    // Queued after the WebView creation on the main thread, so it's destroyed even when the latch timed out
    executor.execute {
        webView?.stopLoading()
        webView?.destroy()
    }

    // evaluateJavascript hands back the result JSON-encoded
    return result?.let { runCatching { Json.decodeFromString<String?>(it) }.getOrNull() }
}

/** Escapes [value] as a JavaScript string literal, or `null` */
fun jsString(value: String?): String = Json.encodeToString(value)
