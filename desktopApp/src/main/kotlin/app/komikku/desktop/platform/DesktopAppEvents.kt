package app.komikku.desktop.platform

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.desktop.AppHiddenEvent
import java.awt.desktop.AppHiddenListener
import java.awt.desktop.QuitResponse
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

/**
 * KMK: application events of the OS (macOS) that AWT otherwise handles on its own.
 *
 * - **Quit** (Cmd+Q, Dock → Quit, logging out): AWT's default quit strategy calls `System.exit(0)` at once, so the app's
 *   bounded shutdown after `application { }` (`stopDesktopWork`) never ran. [installQuitHandler] closes the app like the
 *   window's close button instead and keeps the OS's [QuitResponse]; [finishQuit] answers it once the work stopped, so
 *   a log-out is not cancelled.
 * - **Hide** (Cmd+H): the window is hidden without being minimised; [addHideListener] reports it like minimising, so
 *   the app lock applies.
 * - **Open URL** (an address of a scheme the app bundle registers, `komikku://…`): [installOpenUriHandler]. AWT keeps
 *   the events that come before a handler is set, such as the one that started the app.
 *
 * All are no-ops where `java.awt.Desktop` does not support them (Windows, Linux, headless).
 */
object DesktopAppEvents {

    private val pendingQuit = AtomicReference<QuitResponse?>()

    /** Routes the OS's quit requests to [onQuit]; `false` when the platform has no quit handler. */
    fun installQuitHandler(onQuit: () -> Unit): Boolean {
        val desktop = desktopSupporting(Desktop.Action.APP_QUIT_HANDLER) ?: return false
        desktop.setQuitHandler { _, response -> onQuitRequest(response, onQuit) }
        return true
    }

    fun removeQuitHandler() {
        desktopSupporting(Desktop.Action.APP_QUIT_HANDLER)?.setQuitHandler(null)
    }

    internal fun onQuitRequest(response: QuitResponse, onQuit: () -> Unit) {
        pendingQuit.getAndSet(response)?.cancelQuit()
        onQuit()
    }

    /** Lets a quit requested by the OS go through (exits the JVM); call when the app's work stopped. */
    fun finishQuit() {
        pendingQuit.getAndSet(null)?.performQuit()
    }

    /** Hands the addresses the OS opens with this app to [onOpen]; `false` when the platform has no such event. */
    fun installOpenUriHandler(onOpen: (URI) -> Unit): Boolean {
        val desktop = desktopSupporting(Desktop.Action.APP_OPEN_URI) ?: return false
        desktop.setOpenURIHandler { event -> onOpen(event.uri) }
        return true
    }

    fun removeOpenUriHandler() {
        desktopSupporting(Desktop.Action.APP_OPEN_URI)?.setOpenURIHandler(null)
    }

    /** Calls [onHidden] / [onShown] when the app is hidden / shown again (Cmd+H); `null` when not supported. */
    fun addHideListener(onHidden: () -> Unit, onShown: () -> Unit): AutoCloseable? {
        val desktop = desktopSupporting(Desktop.Action.APP_EVENT_HIDDEN) ?: return null
        val listener = hideListener(onHidden, onShown)
        desktop.addAppEventListener(listener)
        return AutoCloseable { desktop.removeAppEventListener(listener) }
    }

    internal fun hideListener(onHidden: () -> Unit, onShown: () -> Unit): AppHiddenListener = object : AppHiddenListener {
        override fun appHidden(e: AppHiddenEvent?) = onHidden()
        override fun appUnhidden(e: AppHiddenEvent?) = onShown()
    }

    private fun desktopSupporting(action: Desktop.Action): Desktop? = try {
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
            Desktop.getDesktop().takeIf { it.isSupported(action) }
        } else {
            null
        }
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "No desktop support for $action" }
        null
    }
}
