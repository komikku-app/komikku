package exh.log

import android.util.Log
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.elvishew.xlog.LogLevel as XLogLevel

private fun Any.xLog(): Logger = XLog.tag(this::class.java.simpleName).build()

private fun Any.xLogStack(): Logger = XLog.tag(this::class.java.simpleName).enableStackTrace(0).build()

// KMK -->
/**
 * XLog.init() runs during App#onCreate, but DI can eagerly construct components (e.g.
 * AndroidSourceManager) whose async init blocks log before that point, or before it re-runs after
 * an early startup crash. XLog throws IllegalStateException from any tag()/build() call made
 * before init() — swallow that here instead of crashing the app over a debug log line, but still
 * surface the message via android.util.Log so it isn't lost entirely.
 */
private inline fun Any.safeXLog(androidLevel: Int, log: Any?, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalStateException) {
        Log.println(androidLevel, this::class.java.simpleName, log.toString())
    }
}

private inline fun Any.safeXLog(androidLevel: Int, log: Any?, e: Throwable, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalStateException) {
        Log.println(androidLevel, this::class.java.simpleName, log.toString() + "\n" + Log.getStackTraceString(e))
    }
}

private fun formatSafely(format: String, args: Array<out Any?>): String =
    runCatching { String.format(format, *args) }.getOrDefault(format)

/**
 * Safe variant of `XLog.tag(tag).build()`, for callers that need to cache a [Logger] instance
 * (e.g. a lazy property on a singleton/worker) instead of using the [xLogE]/[xLogW]/[xLogD]/[xLogI]
 * convenience functions above. Returns null instead of throwing if accessed before [XLog.init],
 * so callers should use `logger?.d(...)` rather than assuming a non-null [Logger].
 */
fun Any.safeXLogTag(tag: String = (this::class.java.enclosingClass?.simpleName ?: this::class.java.simpleName)): Logger? = try {
    XLog.tag(tag).build()
} catch (_: IllegalStateException) {
    null
}

fun Any.safeXLogStackTag(tag: String = (this::class.java.enclosingClass?.simpleName ?: this::class.java.simpleName)): Logger? = try {
    XLog.tag(tag).enableStackTrace(0).build()
} catch (_: IllegalStateException) {
    null
}
// KMK <--

fun Any.xLogE(log: String) = safeXLog(Log.ERROR, log) { xLog().e(log) }
fun Any.xLogW(log: String) = safeXLog(Log.WARN, log) { xLog().w(log) }
fun Any.xLogD(log: String) = safeXLog(Log.DEBUG, log) { xLog().d(log) }
fun Any.xLogI(log: String) = safeXLog(Log.INFO, log) { xLog().i(log) }
fun Any.xLog(logLevel: LogLevel, log: String) {
    if (logLevel is LogLevel.None) return
    safeXLog(logLevel.androidLevel, log) { xLog().log(logLevel.int, log) }
}
fun Any.xLogJson(log: String) = safeXLog(Log.DEBUG, log) { xLog().json(log) }
fun Any.xLogXML(log: String) = safeXLog(Log.DEBUG, log) { xLog().xml(log) }

fun Any.xLogE(log: String, e: Throwable) = safeXLog(Log.ERROR, log, e) { xLogStack().e(log, e) }
fun Any.xLogW(log: String, e: Throwable) = safeXLog(Log.WARN, log, e) { xLogStack().w(log, e) }
fun Any.xLogD(log: String, e: Throwable) = safeXLog(Log.DEBUG, log, e) { xLogStack().d(log, e) }
fun Any.xLogI(log: String, e: Throwable) = safeXLog(Log.INFO, log, e) { xLogStack().i(log, e) }
fun Any.xLog(logLevel: LogLevel, log: String, e: Throwable) {
    if (logLevel is LogLevel.None) return
    safeXLog(logLevel.androidLevel, log, e) { xLogStack().log(logLevel.int, log, e) }
}

fun Any.xLogE(log: Any?) = safeXLog(Log.ERROR, log) { xLog().let { if (log == null) it.e("null") else it.e(log) } }
fun Any.xLogW(log: Any?) = safeXLog(Log.WARN, log) { xLog().let { if (log == null) it.w("null") else it.w(log) } }
fun Any.xLogD(log: Any?) = safeXLog(Log.DEBUG, log) { xLog().let { if (log == null) it.d("null") else it.d(log) } }
fun Any.xLogI(log: Any?) = safeXLog(Log.INFO, log) { xLog().let { if (log == null) it.i("null") else it.i(log) } }
fun Any.xLog(
    logLevel: LogLevel,
    log: Any?,
) {
    if (logLevel is LogLevel.None) return
    safeXLog(logLevel.androidLevel, log) {
        xLog().let { if (log == null) it.log(logLevel.int, "null") else it.log(logLevel.int, log) }
    }
}

/*fun Any.xLogE(vararg logs: Any) = xLog().e(logs)
fun Any.xLogW(vararg logs: Any) = xLog().w(logs)
fun Any.xLogD(vararg logs: Any) = xLog().d(logs)
fun Any.xLogI(vararg logs: Any) = xLog().i(logs)
fun Any.xLog(logLevel: LogLevel, vararg logs: Any) = xLog().log(logLevel.int, logs)*/

fun Any.xLogE(format: String, vararg args: Any?) {
    val msg = formatSafely(format, args)
    safeXLog(Log.ERROR, msg) { xLog().e(msg) }
}
fun Any.xLogW(format: String, vararg args: Any?) {
    val msg = formatSafely(format, args)
    safeXLog(Log.WARN, msg) { xLog().w(msg) }
}
fun Any.xLogD(format: String, vararg args: Any?) {
    val msg = formatSafely(format, args)
    safeXLog(Log.DEBUG, msg) { xLog().d(msg) }
}
fun Any.xLogI(format: String, vararg args: Any?) {
    val msg = formatSafely(format, args)
    safeXLog(Log.INFO, msg) { xLog().i(msg) }
}
fun Any.xLog(logLevel: LogLevel, format: String, vararg args: Any) {
    if (logLevel is LogLevel.None) return
    val msg = formatSafely(format, args)
    safeXLog(logLevel.androidLevel, msg) { xLog().log(logLevel.int, msg) }
}

sealed class LogLevel(val int: Int, val androidLevel: Int) {
    object None : LogLevel(XLogLevel.NONE, Log.ASSERT)
    object Error : LogLevel(XLogLevel.ERROR, Log.ERROR)
    object Warn : LogLevel(XLogLevel.WARN, Log.WARN)
    object Info : LogLevel(XLogLevel.INFO, Log.INFO)
    object Debug : LogLevel(XLogLevel.DEBUG, Log.DEBUG)
    object Verbose : LogLevel(XLogLevel.VERBOSE, Log.VERBOSE)
    object All : LogLevel(XLogLevel.ALL, Log.VERBOSE)

    val name get() = getLevelName(this)
    val shortName get() = getLevelShortName(this)

    companion object {
        fun getLevelName(logLevel: LogLevel): String = XLogLevel.getLevelName(logLevel.int)
        fun getLevelShortName(logLevel: LogLevel): String = XLogLevel.getShortLevelName(logLevel.int)

        fun values() = listOf(
            None,
            Error,
            Warn,
            Info,
            Debug,
            Verbose,
            All,
        )
    }
}

@Deprecated("Use proper throwable function", ReplaceWith("""xLogE("", log)"""))
fun Any.xLogE(log: Throwable) = safeXLog(Log.ERROR, log.toString(), log) { xLogStack().e(log) }

@Deprecated("Use proper throwable function", ReplaceWith("""xLogW("", log)"""))
fun Any.xLogW(log: Throwable) = safeXLog(Log.WARN, log.toString(), log) { xLogStack().w(log) }

@Deprecated("Use proper throwable function", ReplaceWith("""xLogD("", log)"""))
fun Any.xLogD(log: Throwable) = safeXLog(Log.DEBUG, log.toString(), log) { xLogStack().d(log) }

@Deprecated("Use proper throwable function", ReplaceWith("""xLogI("", log)"""))
fun Any.xLogI(log: Throwable) = safeXLog(Log.INFO, log.toString(), log) { xLogStack().i(log) }

@Deprecated("Use proper throwable function", ReplaceWith("""xLog(logLevel, "", log)"""))
fun Any.xLog(logLevel: LogLevel, log: Throwable) {
    if (logLevel is LogLevel.None) return
    safeXLog(logLevel.androidLevel, log.toString(), log) { xLogStack().log(logLevel.int, log) }
}
