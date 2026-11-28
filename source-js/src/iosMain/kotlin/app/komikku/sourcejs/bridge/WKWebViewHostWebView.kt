package app.komikku.sourcejs.bridge

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSLog
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.create
import platform.Foundation.setValue
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.WebKit.WKAudiovisualMediaTypeAll
import platform.WebKit.WKContentWorld
import platform.WebKit.WKErrorDomain
import platform.WebKit.WKErrorJavaScriptResultTypeIsUnsupported
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerWithReplyProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.concurrent.AtomicInt
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * `evaluateJavascriptViaWebview` (contract 4.4) on a `WKWebView`, with Mangayomi's contract (its
 * `_evaluateJavascriptViaWebview`, a flutter_inappwebview `HeadlessInAppWebView`):
 *
 * - [url] is loaded with [headers] (an `NSMutableURLRequest`). Only `http` / `https` URLs: anything else fails with an
 *   [IllegalArgumentException] before a web view exists (the bridge reports a catchable error).
 * - Every page and frame gets `window.flutter_inappwebview.callHandler(name, ...args)` at document start, returning a
 *   promise. `callHandler('setResponse', value)` with a string `value` ends the call with that value, from any frame
 *   (as in flutter_inappwebview, whose handlers do not check the frame); any other value rejects the promise
 *   (Mangayomi's handler casts it to `String`) and the call goes on. Other handler names resolve with nothing.
 * - Each time the main frame finishes loading, the `flutterInAppWebViewPlatformReady` event is dispatched and then
 *   [scripts] run in order in the main frame (a script that throws is logged, the next one still runs).
 * - `timeoutSeconds` bounds the whole call (the wait for a web view, the cookie copy, the load and the scripts); a
 *   call that times out returns `""` at its deadline, as do a failed first navigation and a crashed web content
 *   process at once (Mangayomi waits for the timeout then, with the same result).
 *
 * The web view is created, driven and torn down on the main thread (`Dispatchers.Main`), one per call; at most
 * [maxConcurrent] exist at once (more calls wait, within their timeout). When the app has a window, the web view is
 * put behind its content with alpha 0.01, as flutter_inappwebview does (WebKit is unreliable with JavaScript in a web
 * view outside the view hierarchy on a device); without one (tests, a headless process) it runs offscreen. When the
 * call ends, for any reason, loading stops, the page is replaced by an empty one (its scripts and timers stop), the
 * message handler and scripts are removed and the view leaves the window, and a navigation still waiting for its
 * host's cookies is cancelled; nothing keeps a reference to it.
 *
 * Cookies: each call has its own `WKWebsiteDataStore.nonPersistentDataStore()` (no disk cache, local storage or
 * cookies left behind). Before the load, the cookies of [cookieStorage] that apply to the URL's host (its domain and
 * parent domains) are copied into it, and before each later navigation to another host (a redirect, a frame) those
 * of that host; after the call, what the page added, changed or deleted is copied back. With the default, the shared
 * `NSHTTPCookieStorage` that [DarwinHostHttp] uses, the page sees the session a source's `Client` requests set up,
 * and cookies the page sets (a JavaScript challenge's clearance) are sent by later `Client` requests, as Mangayomi's
 * HTTP client sends the cookies its web view saved. `null` keeps every call isolated.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class WKWebViewHostWebView(
    private val cookieStorage: NSHTTPCookieStorage? = NSHTTPCookieStorage.sharedHTTPCookieStorage,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    /** The view the web view is put in (behind its content); `null` runs it offscreen. Called on the main thread. */
    private val container: () -> UIView? = ::keyWindowOrNull,
    private val log: (String) -> Unit = ::nsLog,
) : HostWebView {

    private val permits = Semaphore(maxConcurrent)
    private val liveCount = AtomicInt(0)

    /** Copies back of calls that timed out or were cancelled: they run after the call returned. */
    private val cookieScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Web views that exist now (created and not yet torn down). */
    internal val liveWebViews: Int get() = liveCount.value

    /** Test hook: called on the main thread with each new web view, before it loads. */
    internal var onWebViewCreated: ((WKWebView) -> Unit)? = null

    /** Test hook: called on the main thread with each host (lower case) before its stored cookies are copied in. */
    internal var beforeCookieCopy: (suspend (String) -> Unit)? = null

    override suspend fun evaluate(url: String, headers: Map<String, String>, scripts: List<String>, timeoutSeconds: Int): String {
        val target = webViewUrl(url)
        val timeout = timeoutSeconds.coerceAtLeast(1).seconds
        val deadline = TimeSource.Monotonic.markNow() + timeout
        val permit = Permit()
        try {
            // The wait for a web view counts toward the timeout. `held` is checked, not the result: a timeout can
            // win the race against an acquire that already succeeded.
            withTimeoutOrNull(timeout) {
                permits.acquire()
                permit.held = true
            }
            if (!permit.held) return ""
            return withContext(Dispatchers.Main) { Call(target, headers, scripts, deadline, permit).run() }
        } finally {
            permit.release()
        }
    }

    /** One call's permit, released once: when its web view is torn down, before the cookies are copied back. */
    private inner class Permit {
        var held = false

        fun release() {
            if (!held) return
            held = false
            permits.release()
        }
    }

    /** What the web view's delegates report to the call, on the main thread. */
    private sealed interface Event {
        data object Finished : Event
        class Failed(val error: NSError) : Event
        data object ProcessTerminated : Event
        class Response(val value: String) : Event
    }

    private inner class Call(
        private val url: NSURL,
        private val headers: Map<String, String>,
        private val scripts: List<String>,
        private val deadline: TimeMark,
        private val permit: Permit,
    ) {
        private val events = Channel<Event>(Channel.UNLIMITED)

        /** Hosts whose stored cookies were copied in (lower case). Main thread only, like everything below. */
        private val cookieHosts = mutableSetOf<String>()

        /**
         * The copied cookies as WebKit's store holds them, read back right after each copy (WebKit may reject or
         * normalise a cookie): what the page's changes are measured against.
         */
        private val baseline = mutableMapOf<String, NSHTTPCookie>()

        /** Whether the first copy (the URL's host) completed, so the page loaded with it. */
        private var copiedIn = false

        suspend fun run(): String = coroutineScope {
            val callScope = this
            val handler = BridgeMessageHandler { body ->
                val message = WebViewBridge.message(body)
                val value = message?.let { WebViewBridge.response(it) }
                if (value is WebViewBridge.Reply.Value) events.trySend(Event.Response(value.text))
                (value as? WebViewBridge.Reply.Rejected)?.message
            }
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeAll
                userContentController.addUserScript(
                    WKUserScript(
                        source = WebViewBridge.BRIDGE_SCRIPT,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false,
                    ),
                )
                userContentController.addScriptMessageHandlerWithReply(handler, WKContentWorld.pageWorld, WebViewBridge.HANDLER_NAME)
            }
            val store = configuration.websiteDataStore.httpCookieStore
            val delegate = NavigationDelegate(events) { host, proceed -> beforeNavigation(store, host, proceed) }
            val window = container()
            val frame = window?.bounds?.useContents { CGRectMake(0.0, 0.0, size.width, size.height) }
                ?: CGRectMake(0.0, 0.0, OFFSCREEN_WIDTH, OFFSCREEN_HEIGHT)
            val webView = autoreleasepool { WKWebView(frame = frame, configuration = configuration) }
            liveCount.incrementAndGet()
            // null: the call timed out (or was cancelled).
            var value: String? = null
            try {
                autoreleasepool { prepare(webView, window, delegate) }
                value = withTimeoutOrNull(-deadline.elapsedNow()) {
                    host(url)?.let { copyCookiesIn(store, it) }
                    copiedIn = true
                    val request = NSMutableURLRequest(uRL = url)
                    headers.forEach { (name, text) -> request.setValue(text, forHTTPHeaderField = name) }
                    webView.loadRequest(request)
                    // In the call's scope: the scripts it launches are cancelled with it, not waited for here.
                    callScope.awaitResponse(webView)
                }
                value ?: ""
            } finally {
                coroutineContext.cancelChildren()
                autoreleasepool { tearDown(webView, configuration.userContentController, delegate) }
                liveCount.decrementAndGet()
                permit.release()
                // Also after a timeout or a cancellation: cookies the page set are kept, as in Mangayomi's store.
                // The web view is gone, the data store stays as long as this copy needs it.
                if (cookieStorage != null && copiedIn) {
                    val copyBack = cookieScope.launch { withTimeoutOrNull(COOKIE_COPY_TIMEOUT) { copyCookiesOut(store) } }
                    // A reported value waits for its cookies, so the source's next `Client` request sends them; a
                    // call that timed out or was cancelled returns at once.
                    if (value != null) copyBack.join()
                }
            }
        }

        private fun prepare(webView: WKWebView, window: UIView?, delegate: NavigationDelegate) {
            webView.navigationDelegate = delegate
            if (window != null) {
                webView.alpha = 0.01
                webView.userInteractionEnabled = false
                window.insertSubview(webView, atIndex = 0)
                window.sendSubviewToBack(webView)
            }
            onWebViewCreated?.invoke(webView)
        }

        private suspend fun CoroutineScope.awaitResponse(webView: WKWebView): String {
            var pageLoaded = false
            for (event in events) {
                when (event) {
                    Event.Finished -> {
                        pageLoaded = true
                        launch { runScripts(webView) }
                    }
                    is Event.Failed -> {
                        if (isIgnorable(event.error)) continue
                        log("navigation to ${url.absoluteString} failed: ${event.error.localizedDescription}")
                        // A failed later navigation leaves the loaded page in place, which can still report.
                        if (!pageLoaded) return ""
                    }
                    Event.ProcessTerminated -> {
                        log("the web content process of ${url.absoluteString} terminated")
                        return ""
                    }
                    is Event.Response -> return event.value
                }
            }
            return ""
        }

        private suspend fun runScripts(webView: WKWebView) {
            webView.evaluate(WebViewBridge.PLATFORM_READY_SCRIPT)
            for (script in scripts) {
                val error = webView.evaluate(script) ?: continue
                if (error.domain == WKErrorDomain && error.code == WKErrorJavaScriptResultTypeIsUnsupported) continue
                log("a script on ${url.absoluteString} failed: ${error.userInfo["WKJavaScriptExceptionMessage"] ?: error.localizedDescription}")
            }
        }

        private suspend fun WKWebView.evaluate(script: String): NSError? = suspendCancellableCoroutine { continuation ->
            evaluateJavaScript(script) { _, error -> continuation.resumeIfActive(error) }
        }

        /**
         * A navigation (the first load, a redirect, a frame, a script's) is about to start: [proceed] once the stored
         * cookies of its host are in the store. Hosts already copied proceed at once.
         */
        private fun CoroutineScope.beforeNavigation(store: WKHTTPCookieStore, host: String?, proceed: () -> Unit) {
            if (cookieStorage == null || !copiedIn || host == null || host.lowercase() in cookieHosts) {
                proceed()
                return
            }
            // Undispatched: the `finally` is entered now, so WebKit's decision handler is called even if the call
            // ends first (WebKit raises an exception for a handler that is never called).
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    copyCookiesIn(store, host)
                } finally {
                    proceed()
                }
            }
        }

        /**
         * Copies the stored cookies that apply to [host] (its domain and parent domains, any path) into [store], then
         * adds what the store made of them to [baseline]. A cookie the store already has (copied for another host,
         * or set by the page) is not copied again: the store's is the newer one.
         */
        private suspend fun copyCookiesIn(store: WKHTTPCookieStore, host: String) {
            val storage = cookieStorage ?: return
            val name = host.lowercase()
            if (!cookieHosts.add(name)) return
            beforeCookieCopy?.invoke(name)
            val first = cookieHosts.size == 1
            val stored = storage.cookies.orEmpty().filterIsInstance<NSHTTPCookie>().filter { cookieApplies(it.domain, name) }
            if (stored.isEmpty()) return
            val present = if (first) emptySet() else store.allCookies().mapTo(mutableSetOf()) { it.key() }
            val copies = stored.filter { it.key() !in present }
            if (copies.isEmpty()) return
            store.setAll(copies)
            // The first copy fills an empty store: all of it is the baseline, whatever WebKit made of the cookies.
            val keys = copies.mapTo(mutableSetOf()) { it.key() }
            store.allCookies().forEach { cookie ->
                val key = cookie.key()
                if ((first || key in keys) && key !in baseline) baseline[key] = cookie
            }
        }

        private suspend fun copyCookiesOut(store: WKHTTPCookieStore) {
            val storage = cookieStorage ?: return
            // `document.cookie` changes reach the store from the web content process, on another connection than
            // this read: give the ones the page made just before it reported a moment to arrive.
            delay(COOKIE_SETTLE)
            val now = NSDate().timeIntervalSince1970
            val after = store.allCookies().associateBy { it.key() }
            // Deleted by the page: in the baseline, gone now, not expired meanwhile (the storage expires its own
            // copy), and still the copied value in the storage (a value the app's HTTP client stored meanwhile wins).
            val deleted = baseline.filter { (key, cookie) ->
                key !in after && (cookie.expiresDate?.timeIntervalSince1970?.let { it > now } ?: true)
            }
            if (deleted.isNotEmpty()) {
                storage.cookies.orEmpty().filterIsInstance<NSHTTPCookie>()
                    .filter { deleted[it.key()]?.value == it.value }
                    .forEach { storage.deleteCookie(it) }
            }
            // Added or changed by the page (or by its responses); cookies it did not touch are not written back.
            after.forEach { (key, cookie) -> if (baseline[key]?.sameAs(cookie) != true) storage.setCookie(cookie) }
        }
    }

    private fun tearDown(webView: WKWebView, controller: WKUserContentController, delegate: NavigationDelegate) {
        delegate.closed = true
        webView.navigationDelegate = null
        webView.stopLoading()
        controller.removeScriptMessageHandlerForName(WebViewBridge.HANDLER_NAME, WKContentWorld.pageWorld)
        controller.removeAllUserScripts()
        // Replaces the page, so its scripts, timers and requests stop now, not when the view is deallocated.
        webView.loadHTMLString("", baseURL = null)
        webView.removeFromSuperview()
    }

    private class NavigationDelegate(
        private val events: Channel<Event>,
        /** Called with the host a navigation goes to; it calls its second argument when the navigation may start. */
        private val beforeNavigation: (String?, () -> Unit) -> Unit,
    ) : NSObject(), WKNavigationDelegateProtocol {
        var closed = false

        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            if (closed) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                return
            }
            // Answered later when the host's cookies are copied first: by then the call may have ended and the page
            // been replaced, and the navigation must not start in the torn-down view.
            beforeNavigation(host(decidePolicyForNavigationAction.request.URL)) {
                decisionHandler(
                    if (closed) {
                        WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                    } else {
                        WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    },
                )
            }
        }

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            if (!closed) events.trySend(Event.Finished)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
            if (!closed) events.trySend(Event.Failed(withError))
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
            if (!closed) events.trySend(Event.Failed(withError))
        }

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            if (!closed) events.trySend(Event.ProcessTerminated)
        }
    }

    /** The `callHandler` messages; [onMessage] returns the rejection message, `null` to resolve with nothing. */
    private class BridgeMessageHandler(private val onMessage: (Any?) -> String?) : NSObject(), WKScriptMessageHandlerWithReplyProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
            replyHandler: (Any?, String?) -> Unit,
        ) {
            replyHandler(null, onMessage(didReceiveScriptMessage.body))
        }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 2

        private const val OFFSCREEN_WIDTH = 390.0
        private const val OFFSCREEN_HEIGHT = 844.0
        private val COOKIE_COPY_TIMEOUT = 2.seconds
        private val COOKIE_SETTLE = 250.milliseconds

        /** Characters an URL may contain as they are; the others are percent-encoded (spaces, non-ASCII). */
        private const val URL_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%"

        /**
         * [url] as the `NSURL` the web view loads: `http` / `https` with a host, spaces and non-ASCII characters
         * percent-encoded (`NSURL` refuses them before iOS 17). Anything else is an [IllegalArgumentException].
         */
        internal fun webViewUrl(url: String): NSURL {
            val text = url.trim()
            val scheme = text.substringBefore(':', missingDelimiterValue = "")
            require(scheme.lowercase() in HTTP_SCHEMES) {
                if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() || it in "+-." }) {
                    "evaluateJavascriptViaWebview needs an http or https URL, not \"${text.take(40)}\""
                } else {
                    "evaluateJavascriptViaWebview needs an http or https URL, not a '$scheme' one"
                }
            }
            val parsed = NSURL.URLWithString(text)
                ?: NSString.create(string = text)
                    .stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.characterSetWithCharactersInString(URL_CHARACTERS))
                    ?.let { NSURL.URLWithString(it) }
            requireNotNull(parsed) { "evaluateJavascriptViaWebview got an invalid URL: \"${text.take(80)}\"" }
            require(parsed.scheme?.lowercase() in HTTP_SCHEMES && !parsed.host.isNullOrEmpty()) {
                "evaluateJavascriptViaWebview got an invalid URL: \"${text.take(80)}\""
            }
            return parsed
        }

        private val HTTP_SCHEMES = setOf("http", "https")

        /** The app's key window, or `null` without a running `UIApplication` (a test process). */
        internal fun keyWindowOrNull(): UIView? {
            // `sharedApplication` is nil in a process that is not an app (the simulator test binary) or that did not
            // run `UIApplicationMain`, and Kotlin types it non-null: calling it on nil crashed the test process. So it
            // is only read in an app bundle, and checked through a nullable parameter the compiler cannot drop.
            if (!isAppProcess) return null
            return try {
                val application = UIApplication.sharedApplication
                if (isNil(application)) return null
                val windows = application.connectedScenes
                    .filterIsInstance<UIWindowScene>()
                    .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
                windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
            } catch (e: Throwable) {
                null
            }
        }

        private fun isNil(value: Any?): Boolean = value == null

        /** Whether this process is an app (its main bundle is an `.app`), where `UIApplicationMain` runs. */
        private val isAppProcess: Boolean by lazy { NSBundle.mainBundle.bundlePath.endsWith(".app") }

        private fun nsLog(message: String) {
            // The line is the format string: escape `%`.
            NSLog("[JS webview] $message".replace("%", "%%"))
        }

        /** Cancelled navigations (a new one replaced it) and WebKit's policy interruptions are not failures. */
        private fun isIgnorable(error: NSError): Boolean =
            (error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled) ||
                (error.domain == WEBKIT_ERROR_DOMAIN && error.code in IGNORED_WEBKIT_ERRORS)

        private const val WEBKIT_ERROR_DOMAIN = "WebKitErrorDomain"

        /** `WebKitErrorFrameLoadInterruptedByPolicyChange`, `WebKitErrorPlugInWillHandleLoad`. */
        private val IGNORED_WEBKIT_ERRORS = setOf(102L, 204L)

        /**
         * A cookie's identity: name, domain and path. The domain keeps its leading dot: a host-only `example.com`
         * cookie and a domain `.example.com` cookie of the same name and path are two cookies.
         */
        internal fun cookieKey(name: String, domain: String, path: String) = "$name\u0000${domain.lowercase()}\u0000$path"

        private fun NSHTTPCookie.key() = cookieKey(name, domain, path)

        /**
         * Whether a cookie of [domain] is copied in for [host] (lower case): the host itself or a parent domain, with
         * or without the leading dot. Any path and either scheme: a later request to the host may need it. Lenient
         * for a domain without a dot (host-only in `NSHTTPCookie`): it is also copied for subdomains, where WebKit
         * does not send it.
         */
        internal fun cookieApplies(domain: String, host: String): Boolean {
            val bare = domain.lowercase().removePrefix(".")
            return bare.isNotEmpty() && (host == bare || host.endsWith(".$bare"))
        }

        /** The host a navigation goes to, lower case; `null` for a URL without one (`about:blank`, `data:`). */
        private fun host(url: NSURL?): String? = url?.host?.takeIf { it.isNotEmpty() }?.lowercase()

        private suspend fun WKHTTPCookieStore.allCookies(): List<NSHTTPCookie> = suspendCancellableCoroutine { continuation ->
            getAllCookies { cookies -> continuation.resumeIfActive(cookies.orEmpty().filterIsInstance<NSHTTPCookie>()) }
        }

        private suspend fun WKHTTPCookieStore.setAll(cookies: List<NSHTTPCookie>) {
            if (cookies.isEmpty()) return
            suspendCancellableCoroutine { continuation ->
                // Completion handlers run on the main thread, like this code.
                var left = cookies.size
                cookies.forEach { cookie ->
                    setCookie(cookie) {
                        left--
                        if (left == 0) continuation.resumeIfActive(Unit)
                    }
                }
            }
        }

        private fun NSHTTPCookie.sameAs(other: NSHTTPCookie) =
            value == other.value &&
                expiresDate?.timeIntervalSince1970?.toLong() == other.expiresDate?.timeIntervalSince1970?.toLong() &&
                isSecure() == other.isSecure() &&
                isHTTPOnly() == other.isHTTPOnly()

        private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
            if (isActive) resume(value)
        }
    }
}

/**
 * The page side of [WKWebViewHostWebView]: flutter_inappwebview's `window.flutter_inappwebview.callHandler` on a
 * WebKit message handler with reply, and what a message means. Platform-free, so it is tested on its own.
 */
internal object WebViewBridge {
    /** The WebKit message handler `callHandler` posts to (flutter_inappwebview's name). */
    const val HANDLER_NAME = "callHandler"

    /** The handler Mangayomi registers: its first argument, a string, is the call's value. */
    const val SET_RESPONSE = "setResponse"

    /**
     * Installed at document start in every frame. Like flutter_inappwebview's bridge, the arguments travel as one
     * JSON text; the promise is WebKit's reply.
     */
    val BRIDGE_SCRIPT = """
        (function () {
          var handlers = window.webkit && window.webkit.messageHandlers;
          var handler = handlers && handlers.$HANDLER_NAME;
          if (!handler) return;
          var post = handler.postMessage.bind(handler);
          var stringify = JSON.stringify;
          var slice = Array.prototype.slice;
          window.flutter_inappwebview = {
            callHandler: function (handlerName) {
              return post({ handlerName: String(handlerName), args: stringify(slice.call(arguments, 1)) });
            }
          };
        })();
    """.trimIndent()

    /** Dispatched in the main frame before the scripts run (flutter_inappwebview, on each load stop). */
    const val PLATFORM_READY_SCRIPT = "window.dispatchEvent(new Event('flutterInAppWebViewPlatformReady'));"

    class Message(val handlerName: String, val args: String)

    sealed interface Reply {
        /** The call's value. */
        class Value(val text: String) : Reply

        /** The promise rejects with [message]. */
        class Rejected(val message: String) : Reply

        /** Another handler: the promise resolves with nothing. */
        data object Ignored : Reply
    }

    /** A `callHandler` message body (a dictionary with `handlerName` and `args`), `null` for anything else. */
    fun message(body: Any?): Message? {
        val map = body as? Map<*, *> ?: return null
        val name = map["handlerName"] as? String ?: return null
        return Message(name, map["args"] as? String ?: "[]")
    }

    /** What [message] means for the call: Mangayomi's `setResponse` handler casts its first argument to a string. */
    fun response(message: Message): Reply {
        if (message.handlerName != SET_RESPONSE) return Reply.Ignored
        val first = try {
            (Json.parseToJsonElement(message.args) as? JsonArray)?.firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }
        return if (first is JsonPrimitive && first.isString) {
            Reply.Value(first.content)
        } else {
            Reply.Rejected("setResponse expects a string, got ${first ?: "nothing"}")
        }
    }
}
