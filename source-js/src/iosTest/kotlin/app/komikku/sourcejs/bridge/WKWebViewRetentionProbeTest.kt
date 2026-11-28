package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.bridge.LoopbackHttpServer.Response
import app.komikku.sourcejs.webKitTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKContentWorld
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerWithReplyProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Temporary: where the iOS web view's deallocation measurement went wrong (docs/kmp-desktop/IOS_CI.md). Prints, does
 * not assert; removed once the run is read.
 */
class WKWebViewRetentionProbeTest {

    private val server = LoopbackHttpServer { request ->
        when (request.target) {
            "/later" -> html(
                "<html><body><script>setTimeout(function () { " +
                    "window.flutter_inappwebview.callHandler('setResponse', 'later'); }, 300);</script></body></html>",
            )
            else -> html("<html><body>plain</body></html>")
        }
    }
    private val base = server.baseUrl

    @AfterTest
    fun stop() {
        server.close()
    }

    @OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
    @Test
    fun probe() = webKitTest(timeoutMillis = 90_000) {
        val host = WKWebViewHostWebView()
        val holder = kotlin.concurrent.AtomicReference<WKWebView?>(null)
        host.onWebViewCreated = { holder.value = it }
        println("PROBE call=${host.evaluate("$base/later", emptyMap(), emptyList(), 20)}")
        host.onWebViewCreated = null
        val weak = weakAndClear(holder)
        // The old measurement: the weak reference read in this frame, which keeps what the read returned.
        val start = TimeSource.Monotonic.markNow()
        while (weak.value != null && start.elapsedNow().inWholeMilliseconds < 3_000) {
            GC.collect()
            delay(200)
        }
        println("PROBE read in the loop's frame: freed=${weak.value == null} after ${start.elapsedNow()}")
        val again = TimeSource.Monotonic.markNow()
        println("PROBE then read in a frame of its own on the main thread: freed=${awaitFreed(listOf(weak)).single()} after ${again.elapsedNow()}")

        // Each part of the host's use of a web view on its own.
        val variants = listOf("load", "load+delegate", "load+handler", "load+eval")
        val weaks = variants.map { variant -> withContext(Dispatchers.Main) { useAndDrop(variant) } }
        val variantStart = TimeSource.Monotonic.markNow()
        val freed = awaitFreed(weaks)
        variants.forEachIndexed { index, variant -> println("PROBE variant $variant: freed=${freed[index]}") }
        println("PROBE variants measured in ${variantStart.elapsedNow()}")
    }

    @OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
    private suspend fun useAndDrop(variant: String): WeakReference<Any> {
        val configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        val handler = ProbeHandler()
        if (variant == "load+handler") {
            configuration.userContentController.addScriptMessageHandlerWithReply(handler, WKContentWorld.pageWorld, "probe")
        }
        val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0), configuration = configuration)
        val delegate = ProbeDelegate()
        if (variant == "load+delegate") webView.navigationDelegate = delegate
        webView.loadRequest(NSMutableURLRequest(uRL = NSURL(string = "$base/plain")))
        withTimeoutOrNull(10_000) {
            delay(100)
            while (webView.isLoading()) delay(50)
        }
        if (variant == "load+eval") {
            val error = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<String> { continuation ->
                    webView.evaluateJavaScript("document.body.textContent") { _, error ->
                        if (continuation.isActive) continuation.resume(error?.localizedDescription ?: "none")
                    }
                }
            }
            println("PROBE eval error=$error")
        }
        println("PROBE $variant url=${webView.URL?.absoluteString} finished=${delegate.finished}")
        webView.navigationDelegate = null
        webView.stopLoading()
        configuration.userContentController.removeScriptMessageHandlerForName("probe", WKContentWorld.pageWorld)
        webView.loadHTMLString("", baseURL = null)
        webView.removeFromSuperview()
        return WeakReference(webView)
    }

    @OptIn(NativeRuntimeApi::class)
    private suspend fun awaitFreed(weak: List<WeakReference<Any>>, timeoutMillis: Long = 8_000): List<Boolean> {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            val freed = weak.map { isFreed(it) }
            if (freed.all { it } || start.elapsedNow().inWholeMilliseconds >= timeoutMillis) return freed
            GC.collect()
            delay(200)
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    private suspend fun isFreed(weak: WeakReference<Any>): Boolean = withContext(Dispatchers.Main) { weak.value == null }

    @OptIn(ExperimentalNativeApi::class)
    private fun weakAndClear(holder: kotlin.concurrent.AtomicReference<WKWebView?>): WeakReference<Any> {
        val weak = WeakReference<Any>(holder.value!!)
        holder.value = null
        return weak
    }

    private class ProbeDelegate : NSObject(), WKNavigationDelegateProtocol {
        var finished = false

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            finished = true
        }
    }

    private class ProbeHandler : NSObject(), WKScriptMessageHandlerWithReplyProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
            replyHandler: (Any?, String?) -> Unit,
        ) {
            replyHandler(null, null)
        }
    }

    private fun html(text: String) =
        Response(200, listOf("Content-Type" to "text/html; charset=utf-8"), text.encodeToByteArray())
}
