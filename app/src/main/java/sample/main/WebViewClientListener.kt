package sample.main

import android.graphics.Bitmap
import io.github.edsuns.adfilter.FilterResult

/**
 * Created by Edsuns@qq.com on 2021/1/2.
 */
interface WebViewClientListener {
    fun onPageStarted(url: String?, favicon: Bitmap?)
    fun progressChanged(newProgress: Int)
    fun onShouldInterceptRequest(result: FilterResult)
}
