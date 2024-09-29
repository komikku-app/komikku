package sample

import android.util.Patterns
import android.webkit.URLUtil

/**
 * Created by Edsuns@qq.com on 2021/1/3.
 */
fun String.stripParamsAndAnchor(): String {
    var result = this
    var index = this.indexOf('?')
    if (index != -1)
        result = this.substring(0, index)
    index = this.indexOf('#')
    if (index != -1)
        result = this.substring(0, index)
    return result
}

fun String.smartUrlFilter(): String? {
    if (URLUtil.isValidUrl(this))
        return this
    if (Patterns.WEB_URL.matcher(this).matches())
        return URLUtil.guessUrl(this)
    return null
}
