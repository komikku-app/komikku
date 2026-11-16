package com.elvishew.xlog

/** android-compat: xLog's `LogLevel` (same values as `android.util.Log`). */
object LogLevel {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ALL = Int.MIN_VALUE
    const val NONE = Int.MAX_VALUE

    @JvmStatic
    fun getLevelName(logLevel: Int): String = when (logLevel) {
        VERBOSE -> "VERBOSE"
        DEBUG -> "DEBUG"
        INFO -> "INFO"
        WARN -> "WARN"
        ERROR -> "ERROR"
        else -> if (logLevel < VERBOSE) "VERBOSE-${VERBOSE - logLevel}" else "ERROR+${logLevel - ERROR}"
    }

    @JvmStatic
    fun getShortLevelName(logLevel: Int): String = when (logLevel) {
        VERBOSE -> "V"
        DEBUG -> "D"
        INFO -> "I"
        WARN -> "W"
        ERROR -> "E"
        else -> if (logLevel < VERBOSE) "V-${VERBOSE - logLevel}" else "E+${logLevel - ERROR}"
    }
}
