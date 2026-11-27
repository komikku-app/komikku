package app.komikku.desktop.notification

import app.komikku.desktop.updater.DesktopOs
import exh.log.xLogW
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * M2: macOS notifications through `/usr/bin/osascript` (AppleScript's `display notification`). On current macOS AWT's
 * `TrayIcon.displayMessage` shows nothing, so only the in-app banner appeared after a library update.
 *
 * The title and text are passed as arguments of a fixed script (`on run argv`), never put into the script or a shell
 * command, so no text can change what runs and nothing needs escaping. Each argument starts with [MARKER], which the
 * script strips: osascript parses its command line for options, so a title starting with `-` (a manga named
 * "-50kg Cinderella" in a download error, or a bare `-e`) would otherwise be read as an option. The marker works
 * whether or not osascript stops at the first non-option argument or honours `--`, neither of which is relied on.
 *
 * Runs on its own daemon thread, never blocks the caller, ends a run after [TIMEOUT_SECONDS] and drops failures
 * silently (logged). [show] returning `true` therefore means "handed to the system" (queued for osascript), not
 * "seen": macOS may still drop it (notifications denied for Script Editor, Focus mode, osascript failing). macOS
 * attributes these notifications to Script Editor (an unsigned app cannot post under its own name without a helper)
 * and asks for the permission on the first one.
 */
class MacOsNotifier(
    private val osascript: File = File(OSASCRIPT),
    private val runner: (List<String>) -> Unit = ::runCommand,
    private val executor: ExecutorService = defaultExecutor(),
) : DesktopNotifier {

    /** `true` once queued for osascript (handed to the system, see the class comment); `false` when not queued. */
    override fun show(notification: DesktopNotification): Boolean {
        val command = command(osascript.path, notification.title, notification.text)
        return try {
            executor.execute {
                try {
                    runner(command)
                } catch (e: Exception) {
                    xLogW("macOS notification: osascript failed ($e)")
                }
            }
            true
        } catch (e: Exception) {
            // Too many queued (a burst of notifications) or shut down
            xLogW("macOS notification: not shown ($e)")
            false
        }
    }

    companion object {
        const val OSASCRIPT = "/usr/bin/osascript"
        const val TIMEOUT_SECONDS = 10L

        /** Put before every argument (and stripped by [SCRIPT]) so that none starts with `-`. */
        const val MARKER = "|"

        /**
         * The whole script; the title is `item 1 of argv`, the text `item 2 of argv`, each without its first character
         * ([MARKER]).
         */
        val SCRIPT = listOf(
            "on run argv",
            "display notification (my unmark(item 2 of argv)) with title (my unmark(item 1 of argv))",
            "end run",
            "on unmark(marked)",
            "if (length of marked) < 2 then return \"\"",
            "return text 2 thru -1 of marked",
            "end unmark",
        )

        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 1000

        /** The osascript command line for [title] / [text] (cleaned by [argument], each after [MARKER]). */
        fun command(osascript: String, title: String, text: String): List<String> =
            listOf(osascript) + SCRIPT.flatMap { listOf("-e", it) } +
                listOf(MARKER + argument(title, MAX_TITLE), MARKER + argument(text, MAX_TEXT))

        /**
         * [value] as an argument: without NUL (it would end the C string of the argument) and other control
         * characters but line breaks, cut to [max] characters (never between the two halves of a surrogate pair).
         */
        internal fun argument(value: String, max: Int): String {
            val clean = value.filter { it == '\n' || !it.isISOControl() }
            if (clean.length <= max) return clean
            val cut = clean.take(max - 1)
            return (if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut) + "…"
        }

        /** Whether this machine can use the backend: macOS with osascript. */
        fun available(os: DesktopOs = DesktopOs.current(), osascript: File = File(OSASCRIPT)): Boolean =
            os == DesktopOs.MacOs && osascript.canExecute()

        private fun runCommand(command: List<String>) {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                xLogW("macOS notification: osascript did not finish in $TIMEOUT_SECONDS s")
            } else if (process.exitValue() != 0) {
                xLogW("macOS notification: osascript exited with ${process.exitValue()}")
            }
        }

        /** One daemon thread, at most 16 waiting notifications. */
        private fun defaultExecutor(): ExecutorService = ThreadPoolExecutor(
            0,
            1,
            30,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(16),
        ) { runnable -> Thread(runnable, "macos-notifier").apply { isDaemon = true } }
    }
}

/**
 * M2: where notifications go. On macOS (with osascript) they are posted through [MacOsNotifier] whether or not the tray
 * icon is on, as Android posts its notifications without any icon setting; elsewhere through the tray icon
 * ([DesktopTray]) while it is there. The library update's progress always goes to the tray icon's tooltip.
 *
 * M2 review fix: nothing is posted while [enabled] is `false` (the "Show system notifications" setting,
 * [DesktopTrayPreferences.systemNotifications], read on every notification), on every backend; [show] then returns
 * `false`. The app's own banners do not go through here and stay.
 */
class DesktopSystemNotifier(
    private val tray: DesktopNotifier,
    private val macOs: DesktopNotifier?,
    private val enabled: () -> Boolean = { true },
) : DesktopNotifier {

    override fun show(notification: DesktopNotification): Boolean =
        enabled() && (macOs ?: tray).show(notification)

    override fun status(text: String?) = tray.status(text)

    companion object {
        /** The notifier for this machine: [MacOsNotifier] on macOS when [macOsAvailable], else the tray only. */
        fun create(
            tray: DesktopTray,
            enabled: () -> Boolean = { true },
            macOsAvailable: Boolean = MacOsNotifier.available(),
        ): DesktopSystemNotifier = DesktopSystemNotifier(tray, if (macOsAvailable) MacOsNotifier() else null, enabled)
    }
}
