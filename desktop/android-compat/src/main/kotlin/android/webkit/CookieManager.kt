package android.webkit

import app.komikku.desktop.compat.DesktopCookieManager
import tachiyomi.core.common.desktop.DesktopDirs

/**
 * android-compat: `android.webkit.CookieManager`. The desktop instance keeps cookies in memory and
 * persists the non-session ones to `cookies.json` in the desktop data dir (see
 * [DesktopCookieManager]). Callbacks run synchronously on the calling thread.
 */
abstract class CookieManager {

    abstract fun setAcceptCookie(accept: Boolean)

    abstract fun acceptCookie(): Boolean

    abstract fun setCookie(url: String, value: String)

    abstract fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?)

    abstract fun getCookie(url: String): String?

    abstract fun removeSessionCookies(callback: ValueCallback<Boolean>?)

    abstract fun removeAllCookies(callback: ValueCallback<Boolean>?)

    abstract fun hasCookies(): Boolean

    abstract fun flush()

    @Deprecated("Use removeSessionCookies(ValueCallback) instead.")
    fun removeSessionCookie() = removeSessionCookies(null)

    @Deprecated("Use removeAllCookies(ValueCallback) instead.")
    fun removeAllCookie() = removeAllCookies(null)

    @Deprecated("The WebView handles removing expired cookies automatically.")
    fun removeExpiredCookie() = Unit

    companion object {
        private val defaultInstance by lazy { DesktopCookieManager(DesktopDirs.dataDir.resolve("cookies.json")) }

        @JvmStatic
        fun getInstance(): CookieManager = defaultInstance

        @JvmStatic
        fun allowFileSchemeCookies(): Boolean = false

        @JvmStatic
        fun setAcceptFileSchemeCookies(accept: Boolean) = Unit
    }
}
