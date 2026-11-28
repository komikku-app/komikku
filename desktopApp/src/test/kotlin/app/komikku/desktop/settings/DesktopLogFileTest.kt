package app.komikku.desktop.settings

import app.komikku.desktop.compat.DesktopLog
import app.komikku.desktop.log.DesktopLogFile
import exh.log.EHLogLevel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * P12: the desktop log file behind Settings → Advanced "Open the log folder": lines appended with their priority and
 * tag, at most two files (the full one renamed once it is past its size), and SY's log level mapped to the desktop
 * log's priority unless the `komikku.log.level` property sets it.
 */
class DesktopLogFileTest {

    @Test
    fun `lines are appended and the file rotates past its size`() {
        val dir = Files.createTempDirectory("komikku-logs").toFile()
        val log = DesktopLogFile(File(dir, "logs"), maxBytes = 200)
        log.append(DesktopLog.ERROR, "Reader", "first")
        log.file.readText() shouldContain "E/Reader: first"
        repeat(10) { log.append(DesktopLog.INFO, "Tag", "line $it of some length") }
        log.previousFile.exists() shouldBe true
        log.file.length() shouldBe log.file.length().coerceAtMost(260)
        dir.walk().filter { it.isFile }.count() shouldBe 2
        log.close()
        // A new instance appends to the same file
        val again = DesktopLogFile(File(dir, "logs"), maxBytes = 10_000)
        val size = again.file.length()
        again.append(DesktopLog.WARN, "Tag", "after restart")
        again.file.length() shouldBe size + again.file.readLines().last().length + 1
        again.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a fresh start creates the log file with its start line`() {
        val dataDir = Files.createTempDirectory("komikku-start").toFile()
        try {
            val log = DesktopLogFile.start(dataDir, version = "9.8.7")

            log.file shouldBe File(DesktopLogFile.directoryFor(dataDir), "komikku.log")
            val line = log.file.readLines().single()
            line shouldContain "I/${DesktopLogFile.START_TAG}: Komikku 9.8.7 on ${System.getProperty("os.name")}"
            line shouldContain "(${System.getProperty("os.arch")}), Java ${System.getProperty("java.runtime.version")}"
            line shouldContain "data folder ${dataDir.absolutePath}"
            log.close()
        } finally {
            dataDir.deleteRecursively()
        }
    }

    @Test
    fun `the log level maps to the desktop log priority, the system property wins`() {
        DesktopLogFile.priorityFor(EHLogLevel.MINIMAL.ordinal) shouldBe DesktopLog.INFO
        DesktopLogFile.priorityFor(EHLogLevel.EXTRA.ordinal) shouldBe DesktopLog.DEBUG
        DesktopLogFile.priorityFor(EHLogLevel.EXTREME.ordinal) shouldBe DesktopLog.VERBOSE
        val before = DesktopLog.minPriority
        try {
            DesktopLogFile.applyLevel(EHLogLevel.EXTREME.ordinal, property = null)
            DesktopLog.minPriority shouldBe DesktopLog.VERBOSE
            DesktopLogFile.applyLevel(EHLogLevel.MINIMAL.ordinal, property = "error")
            DesktopLog.minPriority shouldBe DesktopLog.VERBOSE
        } finally {
            DesktopLog.minPriority = before
        }
    }
}
