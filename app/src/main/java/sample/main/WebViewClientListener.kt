package sample.main

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import io.github.edsuns.adfilter.FilterResult

/**
 * Created by Edsuns@qq.com on 2021/1/2.
 */
interface WebViewClientListener {
    /**
     * Should be called in [WebClient.onPageStarted],
     * an override of [WebViewClient.onPageStarted],
     * to perform necessary actions when page starts loading, such as
     * reset blocked requests info when filters settings changed.
     */
    fun onPageStarted(
        url: String?,
        favicon: Bitmap?,
    )

    /**
     * Should be called in [ChromeClient.onProgressChanged],
     * an override of [WebChromeClient.onProgressChanged],
     * to perform necessary actions when page starts loading, such as
     * reset blocked requests info when filters settings changed.
     *
     * This function normally will be called before [onPageStarted] is called.
     */
    fun progressChanged(newProgress: Int)

    /**
     * Should be called in [WebClient.shouldInterceptRequest],
     * an override of [WebViewClient.shouldInterceptRequest],
     * to perform necessary actions each time a request is made, such as
     * log filtering results of requests.
     */
    fun onShouldInterceptRequest(result: FilterResult)
}
