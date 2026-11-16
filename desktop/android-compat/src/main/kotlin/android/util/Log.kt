package android.util

import app.komikku.desktop.compat.DesktopLog

/** android-compat: `android.util.Log`, printing through [DesktopLog]. */
object Log {

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @JvmStatic
    fun v(tag: String?, msg: String): Int = println(VERBOSE, tag, msg)

    @JvmStatic
    fun v(tag: String?, msg: String?, tr: Throwable?): Int = log(VERBOSE, tag, msg, tr)

    @JvmStatic
    fun d(tag: String?, msg: String): Int = println(DEBUG, tag, msg)

    @JvmStatic
    fun d(tag: String?, msg: String?, tr: Throwable?): Int = log(DEBUG, tag, msg, tr)

    @JvmStatic
    fun i(tag: String?, msg: String): Int = println(INFO, tag, msg)

    @JvmStatic
    fun i(tag: String?, msg: String?, tr: Throwable?): Int = log(INFO, tag, msg, tr)

    @JvmStatic
    fun w(tag: String?, msg: String): Int = println(WARN, tag, msg)

    @JvmStatic
    fun w(tag: String?, msg: String?, tr: Throwable?): Int = log(WARN, tag, msg, tr)

    @JvmStatic
    fun w(tag: String?, tr: Throwable?): Int = log(WARN, tag, null, tr)

    @JvmStatic
    fun e(tag: String?, msg: String): Int = println(ERROR, tag, msg)

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable?): Int = log(ERROR, tag, msg, tr)

    @JvmStatic
    fun wtf(tag: String?, msg: String?): Int = log(ASSERT, tag, msg, null)

    @JvmStatic
    fun wtf(tag: String?, tr: Throwable): Int = log(ASSERT, tag, tr.message, tr)

    @JvmStatic
    fun wtf(tag: String?, msg: String?, tr: Throwable?): Int = log(ASSERT, tag, msg, tr)

    @JvmStatic
    fun isLoggable(tag: String?, level: Int): Boolean = DesktopLog.isLoggable(level)

    @JvmStatic
    fun getStackTraceString(tr: Throwable?): String = DesktopLog.stackTraceString(tr)

    @JvmStatic
    fun println(priority: Int, tag: String?, msg: String): Int {
        DesktopLog.log(priority, tag, msg)
        return msg.length
    }

    private fun log(priority: Int, tag: String?, msg: String?, tr: Throwable?): Int {
        val text = msg.orEmpty()
        DesktopLog.log(priority, tag, text, tr)
        return text.length
    }
}
