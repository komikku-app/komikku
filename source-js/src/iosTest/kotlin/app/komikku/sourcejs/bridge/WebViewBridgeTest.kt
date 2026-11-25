package app.komikku.sourcejs.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The message logic of [WKWebViewHostWebView] without WebKit: what a `callHandler` message means for the call. */
class WebViewBridgeTest {

    private fun reply(name: String, args: String) = WebViewBridge.response(WebViewBridge.Message(name, args))

    @Test
    fun setResponseWithAStringIsTheValue() {
        assertEquals("abc", assertIs<WebViewBridge.Reply.Value>(reply("setResponse", """["abc"]""")).text)
        assertEquals("", assertIs<WebViewBridge.Reply.Value>(reply("setResponse", """[""]""")).text)
        // Further arguments are ignored, like Mangayomi's `args[0]`.
        assertEquals("{\"a\":1}", assertIs<WebViewBridge.Reply.Value>(reply("setResponse", """["{\"a\":1}", 2]""")).text)
    }

    @Test
    fun setResponseWithAnythingElseRejects() {
        for (args in listOf("[42]", "[null]", "[]", """[{"a":1}]""", "[true]", "not json", "{}")) {
            val rejected = assertIs<WebViewBridge.Reply.Rejected>(reply("setResponse", args), args)
            assertTrue(rejected.message.startsWith("setResponse expects a string"), rejected.message)
        }
    }

    @Test
    fun otherHandlersAreIgnored() {
        assertIs<WebViewBridge.Reply.Ignored>(reply("onLoad", """["abc"]"""))
        assertIs<WebViewBridge.Reply.Ignored>(reply("SetResponse", """["abc"]"""))
    }

    @Test
    fun readsTheMessageBody() {
        val message = WebViewBridge.message(mapOf("handlerName" to "setResponse", "args" to """["x"]"""))!!
        assertEquals("setResponse", message.handlerName)
        assertEquals("""["x"]""", message.args)
        assertEquals("[]", WebViewBridge.message(mapOf("handlerName" to "setResponse"))!!.args)
        assertNull(WebViewBridge.message("setResponse"))
        assertNull(WebViewBridge.message(mapOf("args" to "[]")))
        assertNull(WebViewBridge.message(null))
    }

    @Test
    fun theBridgeIsFlutterInAppWebViewsCallHandler() {
        assertTrue("window.flutter_inappwebview = {" in WebViewBridge.BRIDGE_SCRIPT, WebViewBridge.BRIDGE_SCRIPT)
        assertTrue("handlers.${WebViewBridge.HANDLER_NAME};" in WebViewBridge.BRIDGE_SCRIPT, WebViewBridge.BRIDGE_SCRIPT)
        assertTrue("flutterInAppWebViewPlatformReady" in WebViewBridge.PLATFORM_READY_SCRIPT)
    }

    @Test
    fun webViewUrls() {
        assertEquals("https://example.org/a?b=c", WKWebViewHostWebView.webViewUrl("  https://example.org/a?b=c ").absoluteString)
        assertEquals("http://example.org/dir%20%C3%A9/x", WKWebViewHostWebView.webViewUrl("http://example.org/dir é/x").absoluteString)
    }
}
