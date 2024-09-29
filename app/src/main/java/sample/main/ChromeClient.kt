package sample.main

import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Created by Edsuns@qq.com on 2021/1/2.
 */
class ChromeClient(private val webViewClientListener: WebViewClientListener) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        webViewClientListener.progressChanged(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) {
            Fullscreen.onShowCustomView(view.context, view, callback)
        }
    }

    override fun onHideCustomView() {
        Fullscreen.onHideCustomView()
    }

    private val transparent: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun getDefaultVideoPoster(): Bitmap {
        // make video play button a transparent image
        return transparent
    }
}
