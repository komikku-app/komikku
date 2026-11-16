package com.elvishew.xlog

import app.komikku.desktop.compat.DesktopLog

/**
 * android-compat: xLog's `Logger`, printing through `DesktopLog` with the logger's tag. Borders,
 * stack-trace decoration and JSON / XML pretty printing are not reproduced.
 */
class Logger internal constructor(
    private val tag: String,
) {

    fun v(msg: String?) = printMessage(LogLevel.VERBOSE, msg)
    fun v(obj: Any?) = printObject(LogLevel.VERBOSE, obj)
    fun v(format: String?, vararg args: Any?) = printFormatted(LogLevel.VERBOSE, format, args)
    fun v(msg: String?, tr: Throwable?) = printMessage(LogLevel.VERBOSE, msg, tr)

    fun d(msg: String?) = printMessage(LogLevel.DEBUG, msg)
    fun d(obj: Any?) = printObject(LogLevel.DEBUG, obj)
    fun d(format: String?, vararg args: Any?) = printFormatted(LogLevel.DEBUG, format, args)
    fun d(msg: String?, tr: Throwable?) = printMessage(LogLevel.DEBUG, msg, tr)

    fun i(msg: String?) = printMessage(LogLevel.INFO, msg)
    fun i(obj: Any?) = printObject(LogLevel.INFO, obj)
    fun i(format: String?, vararg args: Any?) = printFormatted(LogLevel.INFO, format, args)
    fun i(msg: String?, tr: Throwable?) = printMessage(LogLevel.INFO, msg, tr)

    fun w(msg: String?) = printMessage(LogLevel.WARN, msg)
    fun w(obj: Any?) = printObject(LogLevel.WARN, obj)
    fun w(format: String?, vararg args: Any?) = printFormatted(LogLevel.WARN, format, args)
    fun w(msg: String?, tr: Throwable?) = printMessage(LogLevel.WARN, msg, tr)

    fun e(msg: String?) = printMessage(LogLevel.ERROR, msg)
    fun e(obj: Any?) = printObject(LogLevel.ERROR, obj)
    fun e(format: String?, vararg args: Any?) = printFormatted(LogLevel.ERROR, format, args)
    fun e(msg: String?, tr: Throwable?) = printMessage(LogLevel.ERROR, msg, tr)

    fun log(logLevel: Int, msg: String?) = printMessage(logLevel, msg)
    fun log(logLevel: Int, obj: Any?) = printObject(logLevel, obj)
    fun log(logLevel: Int, format: String?, vararg args: Any?) = printFormatted(logLevel, format, args)
    fun log(logLevel: Int, msg: String?, tr: Throwable?) = printMessage(logLevel, msg, tr)

    fun json(json: String?) = printMessage(LogLevel.DEBUG, json)
    fun xml(xml: String?) = printMessage(LogLevel.DEBUG, xml)

    private fun printMessage(level: Int, msg: String?, tr: Throwable? = null) {
        if (level == LogLevel.NONE || level < XLog.logLevel) return
        DesktopLog.log(level.coerceIn(LogLevel.VERBOSE, DesktopLog.ASSERT), tag, msg ?: "null", tr)
    }

    private fun printObject(level: Int, obj: Any?) {
        if (obj is Throwable) printMessage(level, obj.toString(), obj) else printMessage(level, obj?.toString())
    }

    private fun printFormatted(level: Int, format: String?, args: Array<out Any?>) {
        val msg = format?.let { runCatching { String.format(it, *args) }.getOrDefault(it) }
        printMessage(level, msg)
    }

    /** android-compat: xLog's `Logger.Builder` (configuration other than the tag is accepted and ignored). */
    class Builder internal constructor() {
        private var tag: String = XLog.DEFAULT_TAG

        fun tag(tag: String): Builder = apply { this.tag = tag }

        fun enableStackTrace(depth: Int): Builder = this

        fun disableStackTrace(): Builder = this

        fun enableThreadInfo(): Builder = this

        fun disableThreadInfo(): Builder = this

        fun enableBorder(): Builder = this

        fun disableBorder(): Builder = this

        fun build(): Logger = Logger(tag)
    }
}
