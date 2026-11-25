package app.komikku.sourcejs

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.posix.getenv
import kotlin.coroutines.resume

/**
 * [mainRunLoopTest] for a test that needs WebKit's own processes. The simulator test task spawns the bare test
 * executable (no app bundle, no `UIApplication`), and there a `WKWebView` never loads anything: WebKit cannot start
 * its web content and networking processes for a process that is not an app (as in XCTest logic tests without a host
 * app). The CI job therefore runs these tests a second time inside an app bundle (`.github/workflows/
 * build_ios_check.yml`, docs/kmp-desktop/IOS_CI.md, I5c), with `KMK_WEBKIT_REQUIRED=1`.
 *
 * Each test first checks once per process that a web view runs JavaScript. If it does not, the test returns at once
 * (printing why), unless `KMK_WEBKIT_REQUIRED` is set: then it fails.
 */
internal fun webKitTest(timeoutMillis: Long = 30_000, block: suspend CoroutineScope.() -> Unit) = mainRunLoopTest(timeoutMillis) {
    if (!WebKitAvailability.works()) {
        val message = "WebKit does not run in this process (bundle ${NSBundle.mainBundle.bundlePath})"
        check(!WebKitAvailability.required) { message }
        println("SKIPPED: $message")
        return@mainRunLoopTest
    }
    block()
}

internal object WebKitAvailability {
    private val lock = Mutex()
    private var works: Boolean? = null

    @OptIn(ExperimentalForeignApi::class)
    val required: Boolean get() = getenv("KMK_WEBKIT_REQUIRED")?.toKString() == "1"

    /** Whether a web view loads a page and runs JavaScript within 10 s (checked once per process). */
    suspend fun works(): Boolean = lock.withLock {
        works ?: probe().also { works = it }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun probe(): Boolean = withContext(Dispatchers.Main) {
        val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 100.0, 100.0), configuration = WKWebViewConfiguration())
        try {
            webView.loadHTMLString("<html><body>probe</body></html>", baseURL = null)
            withTimeoutOrNull(10_000) {
                var text: String? = null
                while (text != "probe") {
                    delay(100)
                    text = withTimeoutOrNull(2_000) {
                        suspendCancellableCoroutine { continuation ->
                            webView.evaluateJavaScript("document.body ? document.body.textContent : ''") { value, _ ->
                                if (continuation.isActive) continuation.resume(value as? String)
                            }
                        }
                    }
                }
                true
            } ?: false
        } finally {
            webView.stopLoading()
        }
    }
}
