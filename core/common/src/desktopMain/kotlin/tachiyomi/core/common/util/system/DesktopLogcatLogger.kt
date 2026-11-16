package tachiyomi.core.common.util.system

import app.komikku.desktop.compat.DesktopLog
import logcat.LogPriority
import logcat.LogcatLogger

/**
 * KMK: desktop backend for `logcat { }` (and therefore [logcat]): forwards to `DesktopLog`, the
 * same sink `android.util.Log` and the xLog shim print to.
 */
object DesktopLogcatLogger : LogcatLogger {

    override fun isLoggable(priority: LogPriority): Boolean = DesktopLog.isLoggable(priority.priorityInt)

    override fun log(priority: LogPriority, tag: String, message: String) {
        DesktopLog.log(priority.priorityInt, tag, message)
    }

    /** Installs this logger once; safe to call repeatedly. */
    fun install() {
        if (!LogcatLogger.isInstalled) LogcatLogger.install()
        if (this !in LogcatLogger.loggers) LogcatLogger.loggers += this
    }
}
