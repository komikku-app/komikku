package app.komikku.desktop.selftest

import app.komikku.desktop.notification.CommandResult
import app.komikku.desktop.notification.DesktopNotification
import app.komikku.desktop.notification.DesktopTray
import app.komikku.desktop.selftest.NotificationSelfTest.TrayResult
import app.komikku.desktop.updater.DesktopOs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `--self-test-notify` (ANSWER 011 (a)): its argument, each OS's backend with fakes, and the report. */
class NotificationSelfTestTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `only --self-test-notify adds the check, with an optional backend`() {
        DesktopSelfTest.notification(arrayOf("--self-test")).shouldBeNull()
        DesktopSelfTest.notification(arrayOf("--self-test", "--self-test-notifyx")).shouldBeNull()
        DesktopSelfTest.notification(arrayOf("--self-test", "--self-test-notify"))!!.backend.shouldBeNull()
        DesktopSelfTest.notification(arrayOf("--self-test", "--self-test-notify="))!!.backend.shouldBeNull()
        DesktopSelfTest.notification(arrayOf("--self-test=r.txt", "--self-test-notify=gdbus"))!!.backend shouldBe "gdbus"
        // Like --self-test-extensions it adds to --self-test, it does not start it
        DesktopSelfTest.isRequested(arrayOf("--self-test-notify")) shouldBe false
        // The plain self-test has no notification check
        DesktopSelfTest.checks.map { it.name }.contains(DesktopSelfTest.NOTIFY_CHECK) shouldBe false
    }

    @Test
    fun `Linux runs notify-send with the fixed notification and reports its exit code`() {
        val commands = mutableListOf<List<String>>()
        val test = linux(path = path("notify-send", "gdbus")) {
            commands += it
            CommandResult.ACCEPTED
        }
        test.run() shouldBe "notify-send exited with 0 (accepted)"
        val command = commands.single()
        File(command.first()).name shouldBe "notify-send"
        command.takeLast(3) shouldContainExactly listOf(
            "--",
            "Komikku self-test",
            "Test notification: \"quotes\" &amp; &lt;tags&gt; \\\\ ü 漫画 \uD83D\uDE00",
        )
    }

    @Test
    fun `Linux tries gdbus after notify-send, like the app, and fails when neither accepts`() {
        val path = path("notify-send", "gdbus")
        linux(path = path) { if (it.name == "gdbus") CommandResult.ACCEPTED else CommandResult(1) }.run() shouldBe
            "notify-send exited with 1; gdbus exited with 0 (accepted)"

        val refused = assertThrows<IllegalStateException> {
            linux(path = path) { if (it.name == "gdbus") CommandResult(1) else CommandResult.TIMED_OUT }.run()
        }
        refused.message shouldBe "notify-send timed out; gdbus exited with 1"

        val missing = assertThrows<IllegalStateException> { linux(path = path("other")) { error("not run") }.run() }
        missing.message shouldBe "notify-send and gdbus not on the PATH (the app would use the tray)"
    }

    @Test
    fun `a Linux backend can be chosen, so both encodings are checked on one machine`() {
        val names = mutableListOf<String>()
        val path = path("notify-send", "gdbus")
        linux(backend = "gdbus", path = path) {
            names += it.name
            CommandResult.ACCEPTED
        }.run() shouldBe "gdbus exited with 0 (accepted)"
        names shouldContainExactly listOf("gdbus")

        // gdbus alone is not followed by anything
        assertThrows<IllegalStateException> { linux(backend = "gdbus", path = path) { CommandResult(1) }.run() }
            .message shouldBe "gdbus exited with 1"
        assertThrows<IllegalStateException> { linux(backend = "gdbus", path = path("notify-send")) { error("no") }.run() }
            .message shouldBe "gdbus not on the PATH (the app would use the tray)"
        assertThrows<IllegalStateException> { linux(backend = "kdialog", path = path) { error("no") }.run() }
            .message shouldBe "unknown backend 'kdialog' (notify-send or gdbus)"
    }

    @Test
    fun `macOS runs osascript and reports its exit code`() {
        val osascript = executable(File(dir, "usr/bin"), "osascript")
        val commands = mutableListOf<List<String>>()
        NotificationSelfTest(os = DesktopOs.MacOs, osascript = osascript, run = {
            commands += it
            CommandResult.ACCEPTED
        }).run() shouldStartWith "osascript exited with 0 (accepted"
        commands.single().first() shouldBe osascript.path
        commands.single().takeLast(2) shouldContainExactly
            listOf("|Komikku self-test", "|" + NotificationSelfTest.TEXT)

        assertThrows<IllegalStateException> {
            NotificationSelfTest(os = DesktopOs.MacOs, osascript = osascript, run = { CommandResult.TIMED_OUT }).run()
        }.message shouldBe "osascript timed out"
        assertThrows<IllegalStateException> {
            NotificationSelfTest(os = DesktopOs.MacOs, osascript = File(dir, "none"), run = { error("not run") }).run()
        }.message shouldContain "not found"
    }

    @Test
    fun `Windows shows the tray message through a temporary icon and reports what failed`() {
        var shown: DesktopNotification? = null
        windows {
            shown = it
            TrayResult(supported = true, iconAdded = true, messageShown = true)
        }.run() shouldStartWith "tray message shown through a temporary tray icon"
        shown shouldBe NotificationSelfTest.NOTIFICATION

        fun failure(result: TrayResult) = assertThrows<IllegalStateException> { windows { result }.run() }.message
        failure(TrayResult(false, false, false)) shouldBe "no system tray (SystemTray.isSupported() is false)"
        failure(TrayResult(true, false, false)) shouldBe "the temporary tray icon could not be added"
        failure(TrayResult(true, true, false)) shouldBe "TrayIcon.displayMessage failed"
        assertThrows<IllegalArgumentException> { windows { throw IllegalArgumentException("awt") }.run() }
            .message shouldBe "awt"
    }

    @Test
    fun `a Windows tray that hangs is a timeout`() {
        val release = CountDownLatch(1)
        try {
            val begin = System.nanoTime()
            assertThrows<IllegalStateException> {
                NotificationSelfTest(os = DesktopOs.Windows, tray = {
                    release.await()
                    TrayResult(true, true, true)
                }, trayTimeoutSeconds = 1).run()
            }.message shouldBe "the tray message timed out after 1 s"
            (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) < 10) shouldBe true
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `the temporary tray icon is not added without a tray`() {
        NotificationSelfTest.trayMessage(NotificationSelfTest.NOTIFICATION, 0, supported = { false }) shouldBe
            TrayResult(false, false, false)
        val noTray = DesktopTray(traySupported = { false })
        NotificationSelfTest.trayMessage(NotificationSelfTest.NOTIFICATION, 0, noTray, supported = { true }) shouldBe
            TrayResult(true, false, false)
        noTray.isInstalled shouldBe false
    }

    @Test
    fun `the report has the notification check's line and the exit code follows it`() {
        val report = File(dir, "report.txt")
        val accepted = linux(path = path("notify-send")) { CommandResult.ACCEPTED }
        DesktopSelfTest.main(report, notification = accepted) shouldBe 0
        val lines = report.readLines()
        lines.last() shouldBe DesktopSelfTest.PASSED
        lines[lines.size - 2] shouldBe "OK   system notification: notify-send exited with 0 (accepted)"

        val refused = linux(path = path("notify-send", "gdbus")) {
            if (it.name == "gdbus") CommandResult.TIMED_OUT else CommandResult(1)
        }
        DesktopSelfTest.main(report, notification = refused) shouldBe 1
        val text = report.readText()
        text.lines().dropLastWhile { it.isEmpty() }.last() shouldBe DesktopSelfTest.FAILED
        text shouldContain "FAIL system notification: java.lang.IllegalStateException: notify-send exited with 1; " +
            "gdbus timed out\n"
        text shouldContain "Stack trace of system notification:\n"
    }

    private val List<String>.name: String get() = File(first()).name

    private fun linux(backend: String? = null, path: String, run: (List<String>) -> CommandResult) =
        NotificationSelfTest(backend = backend, os = DesktopOs.Linux, path = path, run = run)

    private fun windows(tray: (DesktopNotification) -> TrayResult) =
        NotificationSelfTest(os = DesktopOs.Windows, tray = tray)

    /** A PATH of one folder with executables [names]. */
    private fun path(vararg names: String): String {
        val bin = File(dir, "bin-${names.joinToString("-")}").apply { mkdirs() }
        names.forEach { executable(bin, it) }
        return bin.path
    }

    private fun executable(folder: File, name: String): File = File(folder.apply { mkdirs() }, name).apply {
        writeText("#!/bin/sh\nexit 0\n")
        setExecutable(true)
    }
}
