package exh.log

import android.util.Log
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.elvishew.xlog.LogLevel as XLogLevel

fun Any.xLog(): Logger = XLog.tag(this::class.java.simpleName).build()

fun Any.xLogStack(): Logger = XLog.tag(this::class.java.simpleName).enableStackTrace(0).build()

fun Any.xLogE(log: String) = xLog().e(log)
fun Any.xLogW(log: String) = xLog().w(log)
fun Any.xLogD(log: String) = xLog().d(log)
fun Any.xLogI(log: String) = xLog().i(log)
fun Any.xLog(logLevel: LogLevel, log: String) = xLog().log(logLevel.int, log)
fun Any.xLogJson(log: String) = xLog().json(log)
fun Any.xLogXML(log: String) = xLog().xml(log)

@Deprecated("Use proper throwable function", ReplaceWith("""xLogE("", log)"""))
fun Any.xLogE(log: Throwable) = xLogStack().e(log)
@Deprecated("Use proper throwable function", ReplaceWith("""xLogW("", log)"""))
fun Any.xLogW(log: Throwable) = xLogStack().w(log)
@Deprecated("Use proper throwable function", ReplaceWith("""xLogD("", log)"""))
fun Any.xLogD(log: Throwable) = xLogStack().d(log)
@Deprecated("Use proper throwable function", ReplaceWith("""xLogI("", log)"""))
fun Any.xLogI(log: Throwable) = xLogStack().i(log)
@Deprecated("Use proper throwable function", ReplaceWith("""xLog(logLevel, "", log)"""))
fun Any.xLog(logLevel: LogLevel, log: Throwable) = xLogStack().log(logLevel.int, log)

fun Any.xLogE(log: String, e: Throwable) = xLogStack().e(log, e)
fun Any.xLogW(log: String, e: Throwable) = xLogStack().w(log, e)
fun Any.xLogD(log: String, e: Throwable) = xLogStack().d(log, e)
fun Any.xLogI(log: String, e: Throwable) = xLogStack().i(log, e)
fun Any.xLog(logLevel: LogLevel, log: String, e: Throwable) = xLogStack().log(logLevel.int, log, e)

fun Any.xLogE(log: Any?) = xLog().let { if (log == null) it.e("null") else it.e(log) }
fun Any.xLogW(log: Any?) = xLog().let { if (log == null) it.w("null") else it.w(log) }
fun Any.xLogD(log: Any?) = xLog().let { if (log == null) it.d("null") else it.d(log) }
fun Any.xLogI(log: Any?) = xLog().let { if (log == null) it.i("null") else it.i(log) }
fun Any.xLog(logLevel: LogLevel, log: Any?) = xLog().let { if (log == null) it.log(logLevel.int, "null") else it.log(logLevel.int, log) }

/*fun Any.xLogE(vararg logs: Any) = xLog().e(logs)
fun Any.xLogW(vararg logs: Any) = xLog().w(logs)
fun Any.xLogD(vararg logs: Any) = xLog().d(logs)
fun Any.xLogI(vararg logs: Any) = xLog().i(logs)
fun Any.xLog(logLevel: LogLevel, vararg logs: Any) = xLog().log(logLevel.int, logs)*/

fun Any.xLogE(format: String, vararg args: Any?) = xLog().e(format, *args)
fun Any.xLogW(format: String, vararg args: Any?) = xLog().w(format, *args)
fun Any.xLogD(format: String, vararg args: Any?) = xLog().d(format, *args)
fun Any.xLogI(format: String, vararg args: Any?) = xLog().i(format, *args)
fun Any.xLog(logLevel: LogLevel, format: String, vararg args: Any) = xLog().log(logLevel.int, format, *args)

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
            All
        )
    }
}
