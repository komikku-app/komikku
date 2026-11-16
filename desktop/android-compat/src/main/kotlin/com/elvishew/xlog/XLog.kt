package com.elvishew.xlog

/**
 * android-compat: xLog's `XLog` entry point. Unlike Android, no `init()` is required; messages go to
 * `DesktopLog`, filtered by [logLevel] and by `DesktopLog.minPriority`.
 */
object XLog {

    internal const val DEFAULT_TAG = "X-LOG"

    @Volatile
    internal var logLevel: Int = LogLevel.ALL

    private val defaultLogger by lazy { Logger(DEFAULT_TAG) }

    @JvmStatic
    fun init() = Unit

    @JvmStatic
    fun init(logLevel: Int) {
        this.logLevel = logLevel
    }

    @JvmStatic
    fun tag(tag: String): Logger.Builder = Logger.Builder().tag(tag)

    @JvmStatic
    fun v(msg: String?) = defaultLogger.v(msg)

    @JvmStatic
    fun d(msg: String?) = defaultLogger.d(msg)

    @JvmStatic
    fun i(msg: String?) = defaultLogger.i(msg)

    @JvmStatic
    fun w(msg: String?) = defaultLogger.w(msg)

    @JvmStatic
    fun e(msg: String?) = defaultLogger.e(msg)

    @JvmStatic
    fun e(msg: String?, tr: Throwable?) = defaultLogger.e(msg, tr)
}
