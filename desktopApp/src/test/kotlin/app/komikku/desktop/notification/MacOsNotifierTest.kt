package app.komikku.desktop.notification

import app.komikku.desktop.updater.DesktopOs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** M2: the macOS notification backend (osascript) and which backend a machine gets. */
class MacOsNotifierTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `title and text are passed as arguments of a fixed script, unchanged`() {
        val title = "Komikku \"quoted\" \\ back'slash"
        val text = "2 new chapters\n\"Frieren\" & \$(rm -rf ~) `id` \\\" end"
        MacOsNotifier.command("/usr/bin/osascript", title, text) shouldContainExactly
            listOf("/usr/bin/osascript") + MacOsNotifier.SCRIPT.flatMap { listOf("-e", it) } +
            listOf(MacOsNotifier.MARKER + title, MacOsNotifier.MARKER + text)
        MacOsNotifier.SCRIPT.none { it.contains(title) || it.contains("Frieren") } shouldBe true
    }

    @Test
    fun `titles and texts starting with a dash are never read as osascript options`() {
        for (value in listOf("-e", "-50kg Cinderella", "--", "-")) {
            val command = MacOsNotifier.command("/usr/bin/osascript", value, value)
            val arguments = command.drop(1 + 2 * MacOsNotifier.SCRIPT.size)
            arguments shouldContainExactly listOf(MacOsNotifier.MARKER + value, MacOsNotifier.MARKER + value)
            arguments.none { it.startsWith("-") } shouldBe true
            // Only the fixed script lines follow an -e
            command.withIndex().filter { it.value == "-e" }.map { command[it.index + 1] } shouldContainExactly
                MacOsNotifier.SCRIPT
        }
        // The script strips the marker before using the arguments
        MacOsNotifier.SCRIPT.joinToString("\n").contains("text 2 thru -1 of") shouldBe true
        MacOsNotifier.MARKER.startsWith("-") shouldBe false
    }

    @Test
    fun `arguments lose control characters but line breaks and are cut`() {
        MacOsNotifier.argument("a\u0000b\tc\nd\u0007", max = 100) shouldBe "abc\nd"
        MacOsNotifier.argument("x".repeat(20), max = 10) shouldBe "x".repeat(9) + "…"
        MacOsNotifier.argument("short", max = 5) shouldBe "short"
    }

    @Test
    fun `cutting never splits a surrogate pair`() {
        val emoji = "\uD83D\uDE00" // one character outside the BMP, two chars
        // The cut would keep only the high surrogate: it is dropped too
        MacOsNotifier.argument("abc$emoji" + "x".repeat(10), max = 5) shouldBe "abc…"
        // A whole pair before the cut stays
        MacOsNotifier.argument("ab$emoji" + "x".repeat(10), max = 5) shouldBe "ab$emoji…"
        MacOsNotifier.argument(emoji.repeat(10), max = 4) shouldBe "$emoji…"
    }

    @Test
    fun `show runs osascript off the caller's thread and ignores failures`() {
        val commands = mutableListOf<List<String>>()
        val caller = Thread.currentThread()
        var ranOn: Thread? = null
        val notifier = MacOsNotifier(
            osascript = File("/usr/bin/osascript"),
            runner = { command ->
                ranOn = Thread.currentThread()
                commands += command
                error("osascript failed")
            },
            executor = OtherThreadExecutor(),
        )
        notifier.show(DesktopNotification(DesktopNotification.Channel.NewChapters, "Title", "Text")) shouldBe true
        commands.single().takeLast(2) shouldContainExactly listOf(MacOsNotifier.MARKER + "Title", MacOsNotifier.MARKER + "Text")
        (ranOn !== caller) shouldBe true
    }

    @Test
    fun `show reports a notification it could not queue`() {
        val notifier = MacOsNotifier(runner = {}, executor = OtherThreadExecutor(reject = true))
        notifier.show(DesktopNotification(DesktopNotification.Channel.Backup, "T", "X")) shouldBe false
    }

    @Test
    fun `macOS with osascript uses it, anything else the tray`() {
        val osascript = File(dir, "osascript").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        MacOsNotifier.available(DesktopOs.MacOs, osascript) shouldBe true
        MacOsNotifier.available(DesktopOs.MacOs, File(dir, "missing")) shouldBe false
        MacOsNotifier.available(DesktopOs.Linux, osascript) shouldBe false
        MacOsNotifier.available(DesktopOs.Windows, osascript) shouldBe false

        val tray = DesktopTray(traySupported = { false })
        val mac = Recording()
        val notification = DesktopNotification(DesktopNotification.Channel.NewChapters, "T", "X")
        // macOS: posted whether or not the tray icon is there
        DesktopSystemNotifier(tray, mac).show(notification) shouldBe true
        mac.shown.single() shouldBeSameInstanceAs notification
        // Elsewhere: the tray (no icon here, so not shown)
        DesktopSystemNotifier(tray, null).show(notification) shouldBe false
        DesktopSystemNotifier.create(tray, macOsAvailable = false).show(notification) shouldBe false
    }

    @Test
    fun `system notifications switched off show nothing on any backend`() {
        val notification = DesktopNotification(DesktopNotification.Channel.NewChapters, "T", "X")
        val tray = RecordingTray()
        val mac = Recording()
        var enabled = false
        // macOS backend
        DesktopSystemNotifier(tray, mac, enabled = { enabled }).show(notification) shouldBe false
        mac.shown shouldBe emptyList()
        // Tray backend (Windows / Linux)
        DesktopSystemNotifier(tray, null, enabled = { enabled }).show(notification) shouldBe false
        tray.shown shouldBe emptyList()
        // The tooltip status is not a notification and stays
        DesktopSystemNotifier(tray, null, enabled = { enabled }).status("Updating")
        tray.statuses shouldContainExactly listOf("Updating")

        // Switched on again (read on every notification): unchanged
        enabled = true
        DesktopSystemNotifier(tray, mac, enabled = { enabled }).show(notification) shouldBe true
        mac.shown.single() shouldBeSameInstanceAs notification
        DesktopSystemNotifier(tray, null, enabled = { enabled }).show(notification) shouldBe true
        tray.shown.single() shouldBeSameInstanceAs notification
    }

    private class RecordingTray : DesktopNotifier {
        val shown = mutableListOf<DesktopNotification>()
        val statuses = mutableListOf<String?>()
        override fun show(notification: DesktopNotification): Boolean = shown.add(notification)
        override fun status(text: String?) {
            statuses += text
        }
    }

    private class Recording : DesktopNotifier {
        val shown = mutableListOf<DesktopNotification>()
        override fun show(notification: DesktopNotification): Boolean = shown.add(notification)
    }

    /** Runs each task on a new thread and waits for it, so the test can check where it ran. */
    private class OtherThreadExecutor(private val reject: Boolean = false) : AbstractExecutorService() {
        override fun execute(command: Runnable) {
            if (reject) throw RejectedExecutionException("full")
            Thread(command).apply {
                start()
                join()
            }
        }
        override fun shutdown() {}
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
