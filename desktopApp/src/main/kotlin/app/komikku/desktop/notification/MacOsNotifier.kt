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
 * command, so no text can change what runs and nothing needs escaping. Runs on its own daemon thread, never blocks
 * the caller, ends a run after [TIMEOUT_SECONDS] and drops failures silently (logged). macOS attributes these
 * notifications to Script Editor (an unsigned app cannot post under its own name without a helper) and asks for the
 * permission on the first one.
 */
class MacOsNotifier(
    private val osascript: File = File(OSASCRIPT),
    private val runner: (List<String>) -> Unit = ::runCommand,
    private val executor: ExecutorService = defaultExecutor(),
) : DesktopNotifier {

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

        /** The whole script; the title is `item 1 of argv`, the text `item 2 of argv`. */
        val SCRIPT = listOf(
            "on run argv",
            "display notification (item 2 of argv) with title (item 1 of argv)",
            "end run",
        )

        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 1000

        /** The osascript command line for [title] / [text] (cleaned by [argument]). */
        fun command(osascript: String, title: String, text: String): List<String> =
            listOf(osascript) + SCRIPT.flatMap { listOf("-e", it) } + listOf(argument(title, MAX_TITLE), argument(text, MAX_TEXT))

        /**
         * [value] as an argument: without NUL (it would end the C string of the argument) and other control
         * characters but line breaks, cut to [max] characters.
         */
        internal fun argument(value: String, max: Int): String {
            val clean = value.filter { it == '\n' || !it.isISOControl() }
            return if (clean.length <= max) clean else clean.take(max - 1) + "…"
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
 */
class DesktopSystemNotifier(
    private val tray: DesktopTray,
    private val macOs: DesktopNotifier?,
) : DesktopNotifier {

    override fun show(notification: DesktopNotification): Boolean = (macOs ?: tray).show(notification)

    override fun status(text: String?) = tray.status(text)

    companion object {
        /** The notifier for this machine: [MacOsNotifier] on macOS when [macOsAvailable], else the tray only. */
        fun create(tray: DesktopTray, macOsAvailable: Boolean = MacOsNotifier.available()): DesktopSystemNotifier =
            DesktopSystemNotifier(tray, if (macOsAvailable) MacOsNotifier() else null)
    }
}
