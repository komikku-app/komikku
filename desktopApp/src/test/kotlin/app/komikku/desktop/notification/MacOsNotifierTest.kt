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
        MacOsNotifier.command("/usr/bin/osascript", title, text) shouldContainExactly listOf(
            "/usr/bin/osascript",
            "-e", "on run argv",
            "-e", "display notification (item 2 of argv) with title (item 1 of argv)",
            "-e", "end run",
            title,
            text,
        )
    }

    @Test
    fun `arguments lose control characters but line breaks and are cut`() {
        MacOsNotifier.argument("a\u0000b\tc\nd\u0007", max = 100) shouldBe "abc\nd"
        MacOsNotifier.argument("x".repeat(20), max = 10) shouldBe "x".repeat(9) + "…"
        MacOsNotifier.argument("short", max = 5) shouldBe "short"
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
        commands.single().takeLast(2) shouldContainExactly listOf("Title", "Text")
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
