package app.komikku.desktop.selftest

import app.komikku.desktop.notification.CommandResult
import app.komikku.desktop.notification.DesktopNotification
import app.komikku.desktop.notification.DesktopTray
import app.komikku.desktop.notification.LinuxNotifier
import app.komikku.desktop.notification.MacOsNotifier
import app.komikku.desktop.notification.runNotificationCommand
import app.komikku.desktop.updater.DesktopOs
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * `--self-test-notify[=<backend>]` (ATLAS_INSTRUCTIONS.md ANSWER 011 (a)): posts one fixed notification ([TITLE],
 * [TEXT]) through the running OS's system notifier, as the app does, and reports whether the backend accepted it. A
 * separate switch: the plain self-test never shows anything.
 *
 * - Linux: `notify-send`, then `gdbus` after a failure (the app's order and command lines, [LinuxNotifier]), each
 *   killed after [LinuxNotifier.TIMEOUT_SECONDS]; passes when one exits with 0. [backend] (`notify-send` or `gdbus`)
 *   runs only that one, so both encodings can be checked on one machine.
 * - macOS: `osascript` ([MacOsNotifier.command]); passes when it exits with 0. It does so also when notifications are
 *   not allowed for Script Editor, which then shows nothing.
 * - Windows: the tray's balloon ([DesktopTray]). AWT shows one only through an icon in the tray, and the self-test has
 *   none, so it goes through the app's temporary icon (the tray setting off), kept for [HOLD_MILLIS] (the toast goes
 *   with its icon) and removed; passes when the icon was added and `TrayIcon.displayMessage` returned normally.
 *   Windows does not report whether the toast was shown.
 *
 * "Accepted" means handed to the system's notification service; whether it appeared is for `dbus-monitor` or the eye.
 */
class NotificationSelfTest(
    val backend: String? = null,
    private val os: DesktopOs = DesktopOs.current(),
    private val path: String? = System.getenv("PATH"),
    private val osascript: File = File(MacOsNotifier.OSASCRIPT),
    private val run: (List<String>) -> CommandResult = { runNotificationCommand(it, LinuxNotifier.TIMEOUT_SECONDS) },
    private val tray: (DesktopNotification) -> TrayResult = { trayMessage(it) },
    private val trayTimeoutSeconds: Long = TRAY_TIMEOUT_SECONDS,
) {

    /** What the tray backend did: whether there is a tray, the icon could be added and the message call returned. */
    data class TrayResult(val supported: Boolean, val iconAdded: Boolean, val messageShown: Boolean)

    /** The check's detail line; throws with what the backend did when it did not accept the notification. */
    fun run(): String = when (os) {
        DesktopOs.Linux -> linux()
        DesktopOs.MacOs -> macOs()
        DesktopOs.Windows -> windows()
    }

    private fun linux(): String {
        val names = when (backend) {
            null -> listOf(LinuxNotifier.NOTIFY_SEND, LinuxNotifier.GDBUS)
            LinuxNotifier.NOTIFY_SEND, LinuxNotifier.GDBUS -> listOf(backend)
            else -> error("unknown backend '$backend' (${LinuxNotifier.NOTIFY_SEND} or ${LinuxNotifier.GDBUS})")
        }
        val commands = names.mapNotNull { name ->
            LinuxNotifier.findOnPath(name, path)?.let { executable ->
                if (name == LinuxNotifier.NOTIFY_SEND) {
                    LinuxNotifier.notifySendCommand(executable, TITLE, TEXT)
                } else {
                    LinuxNotifier.gdbusCommand(executable, TITLE, TEXT)
                }
            }
        }
        check(commands.isNotEmpty()) { "${names.joinToString(" and ")} not on the PATH (the app would use the tray)" }
        val attempts = mutableListOf<String>()
        for (command in commands) {
            val result = run(command)
            attempts += "${File(command.first()).name} $result"
            if (result.accepted) return attempts.joinToString("; ") + " (accepted)"
        }
        error(attempts.joinToString("; "))
    }

    private fun macOs(): String {
        check(backend == null) { "no backend choice on macOS ('$backend')" }
        check(MacOsNotifier.available(os, osascript)) { "${osascript.path} not found" }
        val result = run(MacOsNotifier.command(osascript.path, TITLE, TEXT))
        check(result.accepted) { "osascript $result" }
        return "osascript $result (accepted; shown only when notifications are allowed for Script Editor)"
    }

    private fun windows(): String {
        check(backend == null) { "no backend choice on Windows ('$backend')" }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "self-test-tray").apply { isDaemon = true } }
        val result = try {
            executor.submit<TrayResult> { tray(NOTIFICATION) }.get(trayTimeoutSeconds, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            error("the tray message timed out after $trayTimeoutSeconds s")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            executor.shutdownNow()
        }
        check(result.supported) { "no system tray (SystemTray.isSupported() is false)" }
        check(result.iconAdded) { "the temporary tray icon could not be added" }
        check(result.messageShown) { "TrayIcon.displayMessage failed" }
        return "tray message shown through a temporary tray icon (added, kept ${HOLD_MILLIS / 1000} s, removed; " +
            "Windows does not report whether the toast appeared)"
    }

    companion object {
        const val TITLE = "Komikku self-test"

        /** Quotes, markup characters, a backslash and characters outside ASCII and the BMP, to check the encodings. */
        const val TEXT = "Test notification: \"quotes\" & <tags> \\ ü 漫画 \uD83D\uDE00"

        const val HOLD_MILLIS = 8_000L
        const val TRAY_TIMEOUT_SECONDS = 30L

        val NOTIFICATION = DesktopNotification(DesktopNotification.Channel.AppUpdate, TITLE, TEXT)

        /**
         * Shows [notification] the way the app does with the tray icon off: through [DesktopTray]'s temporary icon
         * (Windows), kept for [holdMillis] (shorter than [DesktopTray.TEMPORARY_ICON_MILLIS]), then removed.
         */
        internal fun trayMessage(
            notification: DesktopNotification,
            holdMillis: Long = HOLD_MILLIS,
            tray: DesktopTray = DesktopTray(),
            supported: () -> Boolean = { !GraphicsEnvironment.isHeadless() && SystemTray.isSupported() },
        ): TrayResult {
            if (!supported()) return TrayResult(false, false, false)
            try {
                val shown = tray.show(notification)
                val iconAdded = tray.hasTemporaryIcon
                if (shown) Thread.sleep(holdMillis)
                return TrayResult(true, iconAdded, shown)
            } finally {
                tray.quit()
            }
        }
    }
}
