package app.komikku.desktop.log

import app.komikku.desktop.compat.DesktopLog
import exh.log.EHLogLevel
import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * KMK: the desktop's log file (P12; Android's "Dump crash logs" writes logcat to a file to share, the desktop keeps one
 * and Settings → Advanced opens its folder). Every message the desktop log prints ([DesktopLog]) is also appended to
 * [file]; once it is larger than [maxBytes] it becomes [previousFile] (the older one is dropped), so at most two
 * files of about that size are kept. Write failures never reach the caller.
 */
class DesktopLogFile(val directory: File, private val maxBytes: Long = 2L * 1024 * 1024) {

    val file: File get() = File(directory, "komikku.log")
    val previousFile: File get() = File(directory, "komikku.1.log")

    private var writer: Writer? = null
    private var size = 0L

    @Synchronized
    fun append(priority: Int, tag: String, message: String) {
        try {
            val line = "${LocalDateTime.now().format(timeFormat)} ${DesktopLog.priorityLetter(priority)}/$tag: $message\n"
            val out = writer ?: open()
            out.write(line)
            out.flush()
            size += line.toByteArray().size
            if (size > maxBytes) rotate()
        } catch (_: Exception) {
            // A log that cannot be written must not break the caller (nor log about itself)
            closeWriter()
        }
    }

    @Synchronized
    fun close() = closeWriter()

    private fun open(): Writer {
        directory.mkdirs()
        size = file.length()
        return FileOutputStream(file, true).bufferedWriter().also { writer = it }
    }

    private fun rotate() {
        closeWriter()
        previousFile.delete()
        file.renameTo(previousFile)
        size = 0
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        writer = null
    }

    companion object {
        private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /** Where the app keeps its log files: `logs` in its data folder. */
        fun directoryFor(dataDir: File): File = File(dataDir, "logs")

        /** The file sink the app installed ([install]), for "Open the log folder". */
        @Volatile
        var current: DesktopLogFile? = null
            private set

        /**
         * Sends the desktop log to [directory]'s log file as well as where it went so far, and writes uncaught
         * exceptions there too (Android's crash logs) before the previous handler runs.
         */
        fun install(directory: File): DesktopLogFile {
            current?.let { return it }
            val logFile = DesktopLogFile(directory)
            val previous = DesktopLog.sink
            DesktopLog.sink = { priority, tag, message ->
                previous(priority, tag, message)
                logFile.append(priority, tag, message)
            }
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                DesktopLog.log(DesktopLog.ERROR, "Crash", "Uncaught exception in ${thread.name}", error)
                handler?.uncaughtException(thread, error)
            }
            current = logFile
            return logFile
        }

        /**
         * The desktop log priority for SY's "Log level" ([EHLogLevel], Android's verbose logging in Komikku): minimal =
         * info, extra = debug, extreme = verbose.
         */
        fun priorityFor(level: Int): Int = when (level) {
            EHLogLevel.MINIMAL.ordinal -> DesktopLog.INFO
            EHLogLevel.EXTRA.ordinal -> DesktopLog.DEBUG
            else -> DesktopLog.VERBOSE
        }

        /** Applies the "Log level" [level] at once, unless the system property `komikku.log.level` sets the level. */
        fun applyLevel(level: Int, property: String? = System.getProperty("komikku.log.level")) {
            if (DesktopLog.priorityFromName(property) != null) return
            DesktopLog.minPriority = priorityFor(level)
        }
    }
}
