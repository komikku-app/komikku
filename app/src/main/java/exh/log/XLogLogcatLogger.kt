package exh.log

import com.elvishew.xlog.XLog
import logcat.LogPriority
import logcat.LogcatLogger

class XLogLogcatLogger : LogcatLogger {

    override fun log(priority: LogPriority, tag: String, message: String) {
        XLog.tag(tag).log(priority.toXLogLevel(), message)
    }

    private fun LogPriority.toXLogLevel(): Int {
        return when (this) {
            LogPriority.ASSERT -> LogLevel.None.int
            LogPriority.ERROR -> LogLevel.Error.int
            LogPriority.WARN -> LogLevel.Warn.int
            LogPriority.INFO -> LogLevel.Info.int
            LogPriority.DEBUG -> LogLevel.Debug.int
            LogPriority.VERBOSE -> LogLevel.Verbose.int
        }
    }
}
