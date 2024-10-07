package sample.main

import android.graphics.Bitmap
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.edsuns.adfilter.AdFilter

/**
 * Created by Edsuns@qq.com on 2021/1/1.
 */
class WebClient(
    private val webViewClientListener: WebViewClientListener,
) : WebViewClient() {
    private val filter = AdFilter.get()

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request!!.url.toString()
        return super.shouldOverrideUrlLoading(view, request) || !URLUtil.isNetworkUrl(url)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val result = filter.shouldIntercept(view!!, request!!)
        webViewClientListener.onShouldInterceptRequest(result)
        return super.shouldInterceptRequest(view, request) ?: result.resourceResponse
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        filter.performScript(view, url)
        webViewClientListener.onPageStarted(url, favicon)
    }
}
