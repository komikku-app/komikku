package app.komikku.desktop.notification

import app.komikku.desktop.updater.DesktopOs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** The Linux notification backend (notify-send, gdbus, the tray as fallback) and which backend a machine gets. */
class LinuxNotifierTest {

    @TempDir
    lateinit var dir: File

    private val notification = DesktopNotification(DesktopNotification.Channel.NewChapters, "Title", "Text")

    @Test
    fun `notify-send gets the title and text as arguments after --`() {
        val title = "Komikku \"quoted\" 'single' \$(rm -rf ~) `id`"
        val text = "2 new chapters\n\"Frieren\" & <b>bold</b> \$HOME; end"
        LinuxNotifier.notifySendCommand("/usr/bin/notify-send", title, text) shouldContainExactly listOf(
            "/usr/bin/notify-send",
            "--app-name=Komikku",
            "--",
            title,
            "2 new chapters\n\"Frieren\" &amp; &lt;b&gt;bold&lt;/b&gt; \$HOME; end",
        )
    }

    @Test
    fun `titles and texts starting with a dash come after -- for notify-send and quoted for gdbus`() {
        for (value in listOf("-e", "-50kg Cinderella", "--", "-", "--app-name=x")) {
            val notifySend = LinuxNotifier.notifySendCommand("notify-send", value, value)
            notifySend.drop(2) shouldContainExactly listOf("--", value, value)

            val gdbus = LinuxNotifier.gdbusCommand("gdbus", value, value)
            val arguments = gdbus.drop(gdbus.indexOf(LinuxNotifier.NOTIFY_METHOD) + 1)
            arguments.none { it.startsWith("-") } shouldBe true
            parseGVariantString(arguments[3]) shouldBe value
            parseGVariantString(arguments[4]) shouldBe value
        }
    }

    @Test
    fun `notify-send's escape expansion of the body gives back the text`() {
        val text = "C:\\new\\tab \\\\ \\x41 end\\"
        val argument = LinuxNotifier.notifySendCommand("notify-send", "T", text).last()
        argument shouldBe "C:\\\\new\\\\tab \\\\\\\\ \\\\x41 end\\\\"
        gStrcompress(argument) shouldBe text
        // The summary is not expanded by notify-send: unchanged
        LinuxNotifier.notifySendCommand("notify-send", "a\\nb", "x")[3] shouldBe "a\\nb"
    }

    @Test
    fun `gdbus calls Notify with typed GVariant arguments`() {
        LinuxNotifier.gdbusCommand("/usr/bin/gdbus", "New chapters", "Frieren\n\"Dandadan\"") shouldContainExactly listOf(
            "/usr/bin/gdbus",
            "call",
            "--session",
            "--timeout",
            "5",
            "--dest",
            "org.freedesktop.Notifications",
            "--object-path",
            "/org/freedesktop/Notifications",
            "--method",
            "org.freedesktop.Notifications.Notify",
            "\"Komikku\"",
            "uint32 0",
            "\"\"",
            "\"New chapters\"",
            "\"Frieren\\n\\\"Dandadan\\\"\"",
            "@as []",
            "@a{sv} {}",
            "int32 -1",
        )
    }

    @Test
    fun `GVariant strings are ASCII and parse back to the text`() {
        val emoji = "\uD83D\uDE00"
        val texts = listOf(
            "plain",
            "",
            "\"double\" 'single' \\back\\slash\\",
            "line 1\nline 2",
            "日本語のタイトル ü ñ — …",
            "emoji $emoji end",
            "\$(rm -rf ~) `id` %s %n {} [] @as <b>",
            "u\\u0041 \\U00000041",
        )
        for (text in texts) {
            val quoted = LinuxNotifier.gvariantString(text)
            quoted.all { it.code in 0x20..0x7E } shouldBe true
            parseGVariantString(quoted) shouldBe text
        }
        LinuxNotifier.gvariantString("é$emoji\"") shouldBe "\"\\u00e9\\U0001f600\\\"\""
    }

    @Test
    fun `long texts are cut on a whole character, control characters and lone surrogates dropped`() {
        val emoji = "\uD83D\uDE00"
        // Cut like the macOS backend: 200 / 1000 characters, never inside a surrogate pair
        LinuxNotifier.summary("x".repeat(500)) shouldBe "x".repeat(199) + "…"
        LinuxNotifier.body("y".repeat(5000)) shouldBe "y".repeat(999) + "…"
        LinuxNotifier.summary("x".repeat(198) + emoji + "zzz") shouldBe "x".repeat(198) + "…"
        LinuxNotifier.summary("x".repeat(197) + emoji + "zzz") shouldBe "x".repeat(197) + emoji + "…"
        // Entities are added after the cut, so the cut never splits one
        LinuxNotifier.body("&".repeat(2000)) shouldBe "&amp;".repeat(999) + "…"
        LinuxNotifier.summary("a\u0000b\tc\u0007") shouldBe "abc"
        LinuxNotifier.body("a\uD800b\uDC00c") shouldBe "a\uFFFDb\uFFFDc"
        // notify-send refuses an empty summary
        LinuxNotifier.summary("") shouldBe "Komikku"
        LinuxNotifier.summary(" \u0000 ") shouldBe "Komikku"

        val longEmoji = emoji.repeat(3000)
        val gdbus = LinuxNotifier.gdbusCommand("gdbus", longEmoji, longEmoji)
        parseGVariantString(gdbus[14]) shouldBe emoji.repeat(99) + "…"
        parseGVariantString(gdbus[15]) shouldBe emoji.repeat(499) + "…"
    }

    @Test
    fun `notify-send on the path is used, else gdbus, else none`() {
        val bin = File(dir, "bin").apply { mkdirs() }
        val other = File(dir, "other").apply { mkdirs() }
        val gdbus = executable(other, "gdbus")
        val notifySend = executable(bin, "notify-send")
        val path = listOf(bin.path, other.path).joinToString(File.pathSeparator)
        val tray = Recording()

        val both = LinuxNotifier.create(tray, path)!!
        both.commands("T", "X").map { it.first() } shouldContainExactly listOf(notifySend.path, gdbus.path)

        notifySend.delete()
        LinuxNotifier.create(tray, path)!!.commands("T", "X").map { it.first() } shouldContainExactly listOf(gdbus.path)
        LinuxNotifier.create(tray, other.path)!!.commands("T", "X").single()[1] shouldBe "call"

        LinuxNotifier.create(tray, bin.path).shouldBeNull()
        LinuxNotifier.create(tray, "").shouldBeNull()
        // A relative PATH entry would run a file from the working directory
        LinuxNotifier.findOnPath("gdbus", "relative${File.pathSeparator}${other.path}") shouldBe gdbus.path
        LinuxNotifier.findOnPath("gdbus", "relative") shouldBe null
        // A non-executable file or a folder does not count
        File(bin, "notify-send").mkdirs()
        LinuxNotifier.findOnPath("notify-send", bin.path) shouldBe null
    }

    @Test
    fun `notify-send that works is the only command run`() {
        val commands = mutableListOf<List<String>>()
        val tray = Recording()
        val caller = Thread.currentThread()
        var ranOn: Thread? = null
        val notifier = LinuxNotifier("notify-send", "gdbus", tray, runner = { command ->
            ranOn = Thread.currentThread()
            commands += command
            CommandResult.ACCEPTED
        }, executor = OtherThreadExecutor())
        notifier.show(notification) shouldBe true
        commands.single().first() shouldBe "notify-send"
        commands.single().takeLast(2) shouldContainExactly listOf("Title", "Text")
        (ranOn !== caller) shouldBe true
        tray.shown shouldBe emptyList()
    }

    @Test
    fun `without notify-send gdbus is run`() {
        val commands = mutableListOf<List<String>>()
        val tray = Recording()
        LinuxNotifier(null, "gdbus", tray, runner = {
            commands += it
            CommandResult.ACCEPTED
        }, executor = OtherThreadExecutor())
            .show(notification) shouldBe true
        commands.single().take(2) shouldContainExactly listOf("gdbus", "call")
        tray.shown shouldBe emptyList()
    }

    @Test
    fun `failing or timed out commands fall back to gdbus, then to the tray, then to nothing`() {
        val commands = mutableListOf<String>()
        val tray = Recording()
        // notify-send fails, gdbus works
        LinuxNotifier("notify-send", "gdbus", tray, runner = {
            commands += it.first()
            if (it.first() == "gdbus") CommandResult.ACCEPTED else CommandResult(1)
        }, executor = OtherThreadExecutor())
            .show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "gdbus")
        tray.shown shouldBe emptyList()

        // Both fail, one of them throwing: the tray's balloon
        commands.clear()
        LinuxNotifier("notify-send", "gdbus", tray, runner = {
            commands += it.first()
            if (it.first() == "gdbus") error("no bus") else CommandResult(1)
        }, executor = OtherThreadExecutor())
            .show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "gdbus")
        tray.shown.single() shouldBeSameInstanceAs notification

        // Both fail and no tray icon: nothing, no exception
        val noTray = DesktopTray(traySupported = { false })
        LinuxNotifier("notify-send", "gdbus", noTray, runner = { CommandResult(1) }, executor = OtherThreadExecutor())
            .show(notification) shouldBe true
        LinuxNotifier("notify-send", null, ThrowingNotifier(), runner = { CommandResult(1) }, executor = OtherThreadExecutor())
            .show(notification) shouldBe true

        // Neither command known: the tray directly, with its answer
        LinuxNotifier(null, null, noTray, runner = { error("not run") }).show(notification) shouldBe false
    }

    @Test
    fun `a command that timed out is skipped for ten minutes, one that refused is not`() {
        val commands = mutableListOf<String>()
        val tray = Recording()
        var now = 0L
        val notifier = LinuxNotifier("notify-send", "gdbus", tray, runner = {
            commands += it.first()
            if (it.first() == "notify-send") CommandResult.TIMED_OUT else CommandResult.ACCEPTED
        }, executor = OtherThreadExecutor(), clock = { now })
        notifier.show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "gdbus")

        // Within ten minutes notify-send is not waited for again
        commands.clear()
        now += TimeUnit.MINUTES.toNanos(9)
        notifier.show(notification) shouldBe true
        commands shouldContainExactly listOf("gdbus")
        // Then it is tried again
        commands.clear()
        now += TimeUnit.MINUTES.toNanos(2)
        notifier.show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "gdbus")
        tray.shown shouldBe emptyList()

        // Both timing out: the tray at once while they are skipped
        commands.clear()
        val silent = LinuxNotifier("notify-send", "gdbus", tray, runner = {
            commands += it.first()
            CommandResult.TIMED_OUT
        }, executor = OtherThreadExecutor(), clock = { now })
        silent.show(notification) shouldBe true
        silent.show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "gdbus")
        tray.shown.size shouldBe 2

        // A refusal (non-zero exit) is not a reason to skip
        commands.clear()
        val refusing = LinuxNotifier("notify-send", null, tray, runner = {
            commands += it.first()
            CommandResult(1)
        }, executor = OtherThreadExecutor(), clock = { now })
        refusing.show(notification) shouldBe true
        refusing.show(notification) shouldBe true
        commands shouldContainExactly listOf("notify-send", "notify-send")
    }

    @Test
    fun `show never blocks the caller and a full queue goes to the tray`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val tray = Recording()
        val executor = LinuxNotifier.defaultExecutor()
        val notifier = LinuxNotifier("notify-send", null, tray, runner = {
            started.countDown()
            release.await()
            CommandResult.ACCEPTED
        }, executor = executor)
        try {
            val begin = System.nanoTime()
            notifier.show(notification) shouldBe true
            started.await(5, TimeUnit.SECONDS) shouldBe true
            // The first one is stuck in notify-send; 16 more wait in the queue
            repeat(16) { notifier.show(notification) shouldBe true }
            (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) < 2) shouldBe true
            tray.shown shouldBe emptyList()
            // The 18th is not queued: the tray shows it at once
            notifier.show(notification) shouldBe true
            tray.shown.single() shouldBeSameInstanceAs notification
        } finally {
            release.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `a command that exits with 0 posted, any other exit code or a hang did not, and a hang is killed`() {
        runNotificationCommand(javaCommand(LinuxNotifierTestExit::class.java, "0"), 10) shouldBe CommandResult.ACCEPTED
        val refused = runNotificationCommand(javaCommand(LinuxNotifierTestExit::class.java, "1"), 10)
        refused shouldBe CommandResult(1)
        refused.accepted shouldBe false
        refused.toString() shouldBe "exited with 1"
        val missing = runNotificationCommand(listOf(File(dir, "missing").path), 10)
        missing.accepted shouldBe false
        missing.toString() shouldStartWith "could not run ("

        val pidFile = File(dir, "pid")
        val begin = System.nanoTime()
        val hang = runNotificationCommand(javaCommand(LinuxNotifierTestHang::class.java, pidFile.path), timeoutSeconds = 5)
        hang shouldBe CommandResult.TIMED_OUT
        hang.toString() shouldBe "timed out"
        (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) < 15) shouldBe true
        val pid = pidFile.readText().trim().toLong()
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false) shouldBe false
    }

    @Test
    fun `the setting off posts nothing on Linux`() {
        val commands = mutableListOf<List<String>>()
        val tray = Recording()
        var enabled = false
        val linux = LinuxNotifier("notify-send", "gdbus", tray, runner = {
            commands += it
            CommandResult.ACCEPTED
        }, executor = OtherThreadExecutor())
        val notifier = DesktopSystemNotifier(tray, linux, enabled = { enabled })
        notifier.show(notification) shouldBe false
        commands shouldBe emptyList()
        tray.shown shouldBe emptyList()
        notifier.status("Updating")
        tray.statuses shouldContainExactly listOf("Updating")

        enabled = true
        notifier.show(notification) shouldBe true
        commands.single().first() shouldBe "notify-send"
        tray.shown shouldBe emptyList()
    }

    @Test
    fun `Linux uses its notifier whether or not there is a tray icon, Windows the tray`() {
        val noTray = DesktopTray(traySupported = { false })
        val linux = Recording()
        var fallbackGiven: DesktopNotifier? = null
        val onLinux = DesktopSystemNotifier.create(noTray, os = DesktopOs.Linux, macOsAvailable = false, linux = {
            fallbackGiven = it
            linux
        })
        onLinux.show(notification) shouldBe true
        linux.shown.single() shouldBeSameInstanceAs notification
        fallbackGiven shouldBeSameInstanceAs noTray

        // Linux without notify-send and gdbus: the tray (none here)
        DesktopSystemNotifier.create(noTray, os = DesktopOs.Linux, macOsAvailable = false, linux = { null })
            .show(notification) shouldBe false
        // Windows: the tray, never the Linux backend
        DesktopSystemNotifier.create(noTray, os = DesktopOs.Windows, macOsAvailable = false, linux = { error("not on Windows") })
            .show(notification) shouldBe false
        DesktopSystemNotifier.create(noTray, os = DesktopOs.MacOs, macOsAvailable = false, linux = { error("not on macOS") })
            .show(notification) shouldBe false
    }

    private fun executable(folder: File, name: String): File = File(folder, name).apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }

    /** `java -cp <test classes and Kotlin> <main> <arguments>`: a child process on any OS. */
    private fun javaCommand(main: Class<*>, vararg arguments: String): List<String> {
        val classPath = listOf(main, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct()
            .joinToString(File.pathSeparator)
        return listOf(File(System.getProperty("java.home"), "bin/java").path, "-cp", classPath, main.name) + arguments
    }

    /** A GVariant text string read back as `g_variant_parse` reads it (GLib's `string_parse`). */
    private fun parseGVariantString(text: String): String {
        val quote = text.first()
        (quote == '"' || quote == '\'') shouldBe true
        text.last() shouldBe quote
        val body = text.substring(1, text.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            (c == quote) shouldBe false
            (c == '\n') shouldBe false
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            when (val escaped = body[i + 1]) {
                'u' -> {
                    out.appendCodePoint(body.substring(i + 2, i + 6).toInt(16))
                    i += 6
                }
                'U' -> {
                    out.appendCodePoint(body.substring(i + 2, i + 10).toInt(16))
                    i += 10
                }
                else -> {
                    out.append(
                        when (escaped) {
                            'a' -> '\u0007'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'v' -> '\u000B'
                            else -> escaped
                        },
                    )
                    i += 2
                }
            }
        }
        return out.toString()
    }

    /** GLib's `g_strcompress`, which notify-send applies to the body (octal escapes left out: none are produced). */
    private fun gStrcompress(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            (i + 1 < text.length) shouldBe true
            out.append(
                when (val escaped = text[i + 1]) {
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'v' -> '\u000B'
                    else -> escaped
                },
            )
            i += 2
        }
        return out.toString()
    }

    private class Recording : DesktopNotifier {
        val shown = mutableListOf<DesktopNotification>()
        val statuses = mutableListOf<String?>()
        override fun show(notification: DesktopNotification): Boolean = shown.add(notification)
        override fun status(text: String?) {
            statuses += text
        }
    }

    private class ThrowingNotifier : DesktopNotifier {
        override fun show(notification: DesktopNotification): Boolean = error("tray failed")
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

/** A child process that exits with its first argument. */
internal object LinuxNotifierTestExit {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(args[0].toInt())
    }
}

/** A child process that writes its process id to the file named by its first argument and then never ends. */
internal object LinuxNotifierTestHang {
    @JvmStatic
    fun main(args: Array<String>) {
        File(args[0]).writeText(ProcessHandle.current().pid().toString())
        Thread.sleep(TimeUnit.MINUTES.toMillis(5))
    }
}
