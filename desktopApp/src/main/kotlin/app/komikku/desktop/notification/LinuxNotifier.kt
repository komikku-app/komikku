package app.komikku.desktop.notification

import exh.log.xLogW
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Linux notifications through the desktop's notification service (`org.freedesktop.Notifications`), so that they do
 * not depend on a tray icon: stock GNOME has no tray, and the tray's balloon was the only way to show one. Like
 * [MacOsNotifier], posted whether or not the tray icon is on; "Show system notifications" governs
 * ([DesktopSystemNotifier]).
 *
 * `notify-send` (libnotify) when it is on the `PATH`, otherwise `gdbus call` (GLib) to the service's `Notify` method.
 * Every text is a separate process argument, never part of a shell command:
 * - `notify-send`: the title and text follow `--`, so a title starting with `-` is not read as an option (GLib's
 *   option parser stops at `--` and drops it). notify-send expands C escapes in the body (`g_strcompress`), so the
 *   body's backslashes are doubled.
 * - `gdbus`: each argument is GVariant text ([gvariantString]: a double-quoted string, `\` `"` and line breaks
 *   escaped, everything outside printable ASCII as `\u` / `\U`, so the argument does not depend on the JVM's
 *   argument encoding); the numbers and containers carry their types (`uint32 0`, `@as []`, `@a{sv} {}`,
 *   `int32 -1`), so the call works without introspecting the service. No argument starts with `-`.
 *
 * The body is markup for most notification servers (`body-markup`), so `&`, `<` and `>` in it are escaped as
 * entities; the summary is plain text. A server without `body-markup` shows the entities as they are (a known
 * limitation, WINDOWS_LINUX_TEST_PLAN.md section 5: GNOME Shell, KDE Plasma, dunst, xfce4-notifyd and mako have it).
 *
 * Runs on its own daemon thread, never blocks the caller, kills a command after [TIMEOUT_SECONDS]. When a command
 * fails the next one is tried (`gdbus` after a failed `notify-send`), then the tray's balloon ([fallback]), then
 * nothing; failures are logged, never shown. A command that timed out (no notification service answering) is
 * skipped for [SKIP_AFTER_TIMEOUT_MINUTES], so later notifications do not each wait for it. [show] returning `true`
 * means queued.
 */
class LinuxNotifier(
    private val notifySend: String?,
    private val gdbus: String?,
    private val fallback: DesktopNotifier,
    private val runner: (List<String>) -> CommandResult = { runNotificationCommand(it, TIMEOUT_SECONDS) },
    private val executor: ExecutorService = defaultExecutor(),
    private val clock: () -> Long = System::nanoTime,
) : DesktopNotifier {

    /** [clock] time until which a command (by its executable) is skipped after it timed out. */
    private val skippedUntil = ConcurrentHashMap<String, Long>()

    override fun show(notification: DesktopNotification): Boolean {
        val commands = commands(notification.title, notification.text)
        if (commands.isEmpty()) return fallback.show(notification)
        return try {
            executor.execute { post(commands, notification) }
            true
        } catch (e: Exception) {
            // Too many queued (a burst of notifications) or shut down
            xLogW("Linux notification: not queued ($e)")
            fallback.show(notification)
        }
    }

    /** The commands to try in order for [title] / [text]. */
    internal fun commands(title: String, text: String): List<List<String>> = listOfNotNull(
        notifySend?.let { notifySendCommand(it, title, text) },
        gdbus?.let { gdbusCommand(it, title, text) },
    )

    private fun post(commands: List<List<String>>, notification: DesktopNotification) {
        for (command in commands) {
            val executable = command.first()
            val skipped = skippedUntil[executable]
            if (skipped != null && clock() - skipped < 0) continue
            val result = try {
                runner(command)
            } catch (e: Exception) {
                CommandResult(null, failure = e.toString())
            }
            if (result.accepted) return
            xLogW("Linux notification: ${File(executable).name} $result")
            if (result.timedOut) skippedUntil[executable] = clock() + TimeUnit.MINUTES.toNanos(SKIP_AFTER_TIMEOUT_MINUTES)
        }
        try {
            fallback.show(notification)
        } catch (e: Exception) {
            xLogW("Linux notification: the tray fallback failed ($e)")
        }
    }

    companion object {
        const val NOTIFY_SEND = "notify-send"
        const val GDBUS = "gdbus"
        const val TIMEOUT_SECONDS = 10L
        const val SKIP_AFTER_TIMEOUT_MINUTES = 10L

        /** `gdbus call`'s own wait for the service's answer (its default is 25 s, beyond [TIMEOUT_SECONDS]). */
        const val GDBUS_TIMEOUT_SECONDS = 5

        const val APP_NAME = "Komikku"
        const val BUS_NAME = "org.freedesktop.Notifications"
        const val OBJECT_PATH = "/org/freedesktop/Notifications"
        const val NOTIFY_METHOD = "$BUS_NAME.Notify"

        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 1000

        /** Where to look when the process has no `PATH`. */
        private const val DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin"

        /**
         * The notifier for this machine, or `null` when neither `notify-send` nor `gdbus` is on [path] (the tray alone
         * then, see [DesktopSystemNotifier]).
         */
        fun create(fallback: DesktopNotifier, path: String? = System.getenv("PATH")): LinuxNotifier? {
            val notifySend = findOnPath(NOTIFY_SEND, path)
            val gdbus = findOnPath(GDBUS, path)
            if (notifySend == null && gdbus == null) return null
            return LinuxNotifier(notifySend, gdbus, fallback)
        }

        /**
         * The first executable [name] in the absolute directories of [path] (relative entries such as `.` are skipped:
         * they would run a file from the working directory).
         */
        fun findOnPath(name: String, path: String?): String? =
            (path ?: DEFAULT_PATH).split(File.pathSeparatorChar)
                .filter { it.isNotEmpty() && File(it).isAbsolute }
                .map { File(it, name) }
                .firstOrNull { it.isFile && it.canExecute() }
                ?.path

        /** `notify-send --app-name=Komikku -- <title> <text>`. */
        fun notifySendCommand(notifySend: String, title: String, text: String): List<String> = listOf(
            notifySend,
            "--app-name=$APP_NAME",
            "--",
            summary(title),
            body(text).replace("\\", "\\\\"),
        )

        /** `gdbus call` of `Notify(app_name s, replaces_id u, app_icon s, summary s, body s, actions as, hints a{sv}, expire_timeout i)`. */
        fun gdbusCommand(gdbus: String, title: String, text: String): List<String> = listOf(
            gdbus,
            "call",
            "--session",
            "--timeout",
            GDBUS_TIMEOUT_SECONDS.toString(),
            "--dest",
            BUS_NAME,
            "--object-path",
            OBJECT_PATH,
            "--method",
            NOTIFY_METHOD,
            gvariantString(APP_NAME),
            "uint32 0",
            gvariantString(""),
            gvariantString(summary(title)),
            gvariantString(body(text)),
            "@as []",
            "@a{sv} {}",
            // The server's default display time
            "int32 -1",
        )

        /** The title as the summary: cleaned and cut like [MacOsNotifier.argument], never empty (notify-send refuses that). */
        internal fun summary(title: String): String =
            clean(title, MAX_TITLE).ifBlank { APP_NAME }

        /** The text as the body: cleaned and cut, then `&`, `<`, `>` as markup entities. */
        internal fun body(text: String): String =
            clean(text, MAX_TEXT).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /** [MacOsNotifier.argument] (no control characters but line breaks, cut on a whole character) without lone surrogates. */
        private fun clean(value: String, max: Int): String {
            val cut = MacOsNotifier.argument(value, max)
            return buildString(cut.length) {
                var i = 0
                while (i < cut.length) {
                    val c = cut[i]
                    when {
                        c.isHighSurrogate() && i + 1 < cut.length && cut[i + 1].isLowSurrogate() -> {
                            append(c).append(cut[i + 1])
                            i++
                        }
                        c.isSurrogate() -> append('\uFFFD')
                        else -> append(c)
                    }
                    i++
                }
            }
        }

        /**
         * [value] as a GVariant text string (`g_variant_parse`): in double quotes, `\` and `"` escaped, a line break
         * as `\n`, every other character outside printable ASCII as `\uXXXX` or, outside the BMP, `\UXXXXXXXX`.
         */
        fun gvariantString(value: String): String = buildString {
            append('"')
            value.codePoints().forEach { codePoint ->
                when {
                    codePoint == '"'.code -> append("\\\"")
                    codePoint == '\\'.code -> append("\\\\")
                    codePoint == '\n'.code -> append("\\n")
                    codePoint in 0x20..0x7E -> append(codePoint.toChar())
                    codePoint <= 0xFFFF -> append("\\u").append(hex(codePoint, 4))
                    else -> append("\\U").append(hex(codePoint, 8))
                }
            }
            append('"')
        }

        private fun hex(value: Int, digits: Int): String = Integer.toHexString(value).padStart(digits, '0')

        /** One daemon thread, at most 16 waiting notifications. */
        internal fun defaultExecutor(): ExecutorService = ThreadPoolExecutor(
            0,
            1,
            30,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(16),
        ) { runnable -> Thread(runnable, "linux-notifier").apply { isDaemon = true } }
    }
}
