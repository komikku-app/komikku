package app.komikku.desktop.compat

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Single desktop log sink shared by `android.util.Log`, the `com.elvishew.xlog` shim and the
 * desktop `logcat` logger, so one place controls what the desktop app prints.
 *
 * Priorities use the `android.util.Log` constants (2 = VERBOSE … 7 = ASSERT). The default sink
 * prints `HH:mm:ss.SSS D/Tag: message` to stderr for messages at or above [minPriority]
 * (system property `komikku.log.level`: verbose, debug, info, warn, error; default info).
 */
object DesktopLog {

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @Volatile
    var minPriority: Int = priorityFromName(System.getProperty("komikku.log.level")) ?: INFO

    /** Receives every message at or above [minPriority]. Replace it to redirect desktop logs. */
    @Volatile
    var sink: (priority: Int, tag: String, message: String) -> Unit = ::printToStderr

    fun isLoggable(priority: Int): Boolean = priority >= minPriority

    fun log(priority: Int, tag: String?, message: String, throwable: Throwable? = null) {
        if (!isLoggable(priority)) return
        val full = if (throwable == null) message else message + "\n" + stackTraceString(throwable)
        sink(priority, tag.orEmpty(), full)
    }

    fun stackTraceString(throwable: Throwable?): String {
        if (throwable == null) return ""
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString().trimEnd()
    }

    fun priorityLetter(priority: Int): Char = when (priority) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        else -> 'A'
    }

    fun priorityFromName(name: String?): Int? = when (name?.trim()?.lowercase()) {
        "verbose", "v", "all" -> VERBOSE
        "debug", "d" -> DEBUG
        "info", "i" -> INFO
        "warn", "warning", "w" -> WARN
        "error", "e" -> ERROR
        "assert", "a", "none" -> ASSERT
        else -> null
    }

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun printToStderr(priority: Int, tag: String, message: String) {
        System.err.println("${LocalTime.now().format(timeFormat)} ${priorityLetter(priority)}/$tag: $message")
    }
}
