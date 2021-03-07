package exh.log

import com.elvishew.xlog.internal.DefaultsFactory
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.backup.BackupStrategy
import com.elvishew.xlog.printer.file.clean.CleanStrategy
import com.elvishew.xlog.printer.file.naming.FileNameGenerator
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import com.elvishew.xlog.flattener.Flattener2 as Flattener

/**
 * Log [Printer] using file system. When print a log, it will print it to the specified file.
 *
 * Use the [Builder] to construct a [EnhancedFilePrinter] object.
 *
 * @param folderPath The folder path of log file.
 * @param fileNameGenerator the file name generator for log file.
 * @param backupStrategy the backup strategy for log file.
 * @param cleanStrategy The clean strategy for log file.
 * @param flattener The flattener when print a log.
 *
 */
@Suppress("unused")
class EnhancedFilePrinter internal constructor(
    private val folderPath: String,
    private val fileNameGenerator: FileNameGenerator,
    private val backupStrategy: BackupStrategy,
    private val cleanStrategy: CleanStrategy,
    private val flattener: Flattener
) : Printer {
    /**
     * Log writer.
     */
    private val writer: Writer

    @Volatile
    private var worker: Worker? = null

    /**
     * Make sure the folder of log file exists.
     */
    private fun checkLogFolder() {
        val folder = File(folderPath)
        if (!folder.exists()) {
            folder.mkdirs()
        }
    }

    override fun println(logLevel: Int, tag: String, msg: String) {
        val timeMillis = System.currentTimeMillis()
        if (USE_WORKER) {
            val worker = worker ?: return
            if (!worker.isStarted()) {
                worker.start()
            }
            worker.enqueue(LogItem(timeMillis, logLevel, tag, msg))
        } else {
            doPrintln(timeMillis, logLevel, tag, msg)
        }
    }

    /**
     * Do the real job of writing log to file.
     */
    private fun doPrintln(timeMillis: Long, logLevel: Int, tag: String, msg: String) {
        var lastFileName = writer.lastFileName
        if (lastFileName == null || fileNameGenerator.isFileNameChangeable) {
            val newFileName = fileNameGenerator.generateFileName(logLevel, System.currentTimeMillis())
            require(!(newFileName == null || newFileName.trim { it <= ' ' }.isEmpty())) { "File name should not be empty." }
            if (newFileName != lastFileName) {
                if (writer.isOpened) {
                    writer.close()
                }
                cleanLogFilesIfNecessary()
                if (writer.open(newFileName).not()) {
                    return
                }
                lastFileName = newFileName
            }
        }
        val lastFile = writer.file ?: return
        if (backupStrategy.shouldBackup(lastFile)) {
            // Backup the log file, and create a new log file.
            writer.close()
            val backupFile = File(folderPath, "$lastFileName.bak")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            lastFile.renameTo(backupFile)
            if (writer.open(lastFileName).not()) {
                return
            }
        }
        val flattenedLog = flattener.flatten(timeMillis, logLevel, tag, msg).toString()
        writer.appendLog(flattenedLog)
    }

    /**
     * Clean log files if should clean follow strategy
     */
    private fun cleanLogFilesIfNecessary() {
        val logDir = File(folderPath)
        logDir.listFiles().orEmpty()
            .asSequence()
            .filter { cleanStrategy.shouldClean(it) }
            .forEach { it.delete() }
    }

    /**
     * Builder for [EnhancedFilePrinter].
     * @param folderPath the folder path of log file
     */
    class Builder(private val folderPath: String) {
        /**
         * The file name generator for log file.
         */
        var fileNameGenerator: FileNameGenerator? = null

        /**
         * The backup strategy for log file.
         */
        var backupStrategy: BackupStrategy? = null

        /**
         * The clean strategy for log file.
         */
        var cleanStrategy: CleanStrategy? = null

        /**
         * The flattener when print a log.
         */
        var flattener: Flattener? = null

        /**
         * Set the file name generator for log file.
         *
         * @param fileNameGenerator the file name generator for log file
         * @return the builder
         */
        fun fileNameGenerator(fileNameGenerator: FileNameGenerator): Builder {
            this.fileNameGenerator = fileNameGenerator
            return this
        }

        /**
         * Set the backup strategy for log file.
         *
         * @param backupStrategy the backup strategy for log file
         * @return the builder
         */
        fun backupStrategy(backupStrategy: BackupStrategy): Builder {
            this.backupStrategy = backupStrategy
            return this
        }

        /**
         * Set the clean strategy for log file.
         *
         * @param cleanStrategy the clean strategy for log file
         * @return the builder
         */
        fun cleanStrategy(cleanStrategy: CleanStrategy): Builder {
            this.cleanStrategy = cleanStrategy
            return this
        }

        /**
         * Set the flattener when print a log.
         *
         * @param flattener the flattener when print a log
         * @return the builder
         */
        fun flattener(flattener: Flattener): Builder {
            this.flattener = flattener
            return this
        }

        /**
         * Build configured [EnhancedFilePrinter] object.
         *
         * @return the built configured [EnhancedFilePrinter] object
         */
        fun build(): EnhancedFilePrinter {
            return EnhancedFilePrinter(
                folderPath,
                fileNameGenerator ?: DefaultsFactory.createFileNameGenerator(),
                backupStrategy ?: DefaultsFactory.createBackupStrategy(),
                cleanStrategy ?: DefaultsFactory.createCleanStrategy(),
                flattener ?: DefaultsFactory.createFlattener2()
            )
        }

        companion object {
            operator fun invoke(folderPath: String, block: Builder.() -> Unit): EnhancedFilePrinter {
                return Builder(folderPath).apply(block).build()
            }
        }
    }

    private data class LogItem(
        var timeMillis: Long,
        var level: Int,
        var tag: String,
        var msg: String,
    )

    /**
     * Work in background, we can enqueue the logs, and the worker will dispatch them.
     */
    private inner class Worker : Runnable {
        private val logs: BlockingQueue<LogItem> = LinkedBlockingQueue()

        @Volatile
        private var started = false

        /**
         * Enqueue the log.
         *
         * @param log the log to be written to file
         */
        fun enqueue(log: LogItem) {
            try {
                logs.put(log)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        /**
         * Whether the worker is started.
         *
         * @return true if started, false otherwise
         */
        fun isStarted(): Boolean {
            synchronized(this) { return started }
        }

        /**
         * Start the worker.
         */
        fun start() {
            synchronized(this) {
                Thread(this).start()
                started = true
            }
        }

        override fun run() {
            try {
                var log: LogItem
                while (logs.take().also { log = it } != null) {
                    doPrintln(log.timeMillis, log.level, log.tag, log.msg)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                synchronized(this) { started = false }
            }
        }
    }

    /**
     * Used to write the flattened logs to the log file.
     */
    private inner class Writer {
        /**
         * Get the name of last used log file.
         * @return the name of last used log file, maybe null
         */
        /**
         * The file name of last used log file.
         */
        var lastFileName: String? = null
            private set
        /**
         * Get the current log file.
         *
         * @return the current log file, maybe null
         */
        /**
         * The current log file.
         */
        var file: File? = null
            private set

        private var bufferedWriter: BufferedWriter? = null

        /**
         * Whether the log file is opened.
         *
         * @return true if opened, false otherwise
         */
        val isOpened: Boolean
            get() = bufferedWriter != null

        /**
         * Open the file of specific name to be written into.
         *
         * @param newFileName the specific file name
         * @return true if opened successfully, false otherwise
         */
        fun open(newFileName: String): Boolean {
            return try {
                val file = File(folderPath, newFileName)
                if (file.exists().not()) {
                    (file.parentFile ?: File(file.absolutePath.substringBeforeLast(File.separatorChar))).mkdirs()
                    file.createNewFile()
                }
                bufferedWriter = FileWriter(file, true).buffered()
                lastFileName = newFileName
                this.file = file
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Close the current log file if it is opened.
         *
         * @return true if closed successfully, false otherwise
         */
        fun close(): Boolean {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return false
                } finally {
                    bufferedWriter = null
                    lastFileName = null
                    file = null
                }
            }
            return true
        }

        /**
         * Append the flattened log to the end of current opened log file.
         *
         * @param flattenedLog the flattened log
         */
        fun appendLog(flattenedLog: String) {
            val bufferedWriter = bufferedWriter
            requireNotNull(bufferedWriter)
            try {
                bufferedWriter.write(flattenedLog)
                bufferedWriter.newLine()
                bufferedWriter.flush()
            } catch (e: IOException) {
            }
        }
    }

    companion object {
        /**
         * Use worker, write logs asynchronously.
         */
        private const val USE_WORKER = true
    }

    init {
        writer = Writer()
        if (USE_WORKER) {
            worker = Worker()
        }
        checkLogFolder()
    }
}
