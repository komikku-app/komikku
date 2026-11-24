package app.komikku.desktop.notification

import exh.log.xLogE
import exh.log.xLogW
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/** P17: the desktop's tray setting (desktop-only key; on by default where the desktop has a tray). */
class DesktopTrayPreferences(private val preferenceStore: PreferenceStore) {
    fun trayIcon() = preferenceStore.getBoolean("desktop_tray_icon", true)
}

/**
 * P17: the optional system tray icon and the notifications shown through it (AWT `SystemTray` / `TrayIcon`; Compose's
 * `Tray` is the same API bound to a composition). Without a tray — switched off, `SystemTray.isSupported()` false (e.g.
 * GNOME without an AppIndicator extension) or a headless environment — nothing is installed and [show] returns `false`:
 * the app works the same, without system notifications (its own banners and snackbars stay). Any AWT failure is logged
 * once and counts as "no tray".
 *
 * The icon opens the window (click, or "Open Komikku" in its menu) and offers "Exit"; clicking a notification opens the
 * window too (where the platform reports such clicks). Its tooltip shows [status] (the library update's progress).
 *
 * [traySupported] is injected for tests (a headless test cannot create a `TrayIcon` at all).
 */
class DesktopTray(
    private val traySupported: () -> Boolean = { !GraphicsEnvironment.isHeadless() && SystemTray.isSupported() },
) : DesktopNotifier {

    @Volatile
    private var icon: TrayIcon? = null

    @Volatile
    private var tooltip: String = APP_NAME

    /** Whether an icon is in the tray now. */
    val isInstalled: Boolean get() = icon != null

    /**
     * Puts the icon into the tray (when there is one); [openText] / [exitText] are its menu's labels, [onOpen] shows the
     * window, [onExit] quits. Does nothing when already installed. Returns whether the icon is there.
     */
    @Synchronized
    fun install(openText: String, exitText: String, onOpen: () -> Unit, onExit: () -> Unit): Boolean {
        if (icon != null) return true
        val supported = try {
            traySupported()
        } catch (e: Throwable) {
            xLogW("System tray: not available ($e)")
            false
        }
        if (!supported) return false
        return try {
            val menu = PopupMenu().apply {
                add(MenuItem(openText).apply { addActionListener { onOpen() } })
                addSeparator()
                add(MenuItem(exitText).apply { addActionListener { onExit() } })
            }
            val trayIcon = TrayIcon(trayImage(), tooltip, menu).apply {
                isImageAutoSize = true
                // A click on the icon (Windows: double click) or on a notification
                addActionListener { onOpen() }
            }
            onEdt { SystemTray.getSystemTray().add(trayIcon) }
            icon = trayIcon
            true
        } catch (e: Throwable) {
            xLogE("System tray: the icon could not be added", e)
            false
        }
    }

    /** Takes the icon out of the tray (the setting was switched off, the app exits). */
    @Synchronized
    fun remove() {
        val trayIcon = icon ?: return
        icon = null
        try {
            onEdt { SystemTray.getSystemTray().remove(trayIcon) }
        } catch (e: Throwable) {
            xLogE("System tray: the icon could not be removed", e)
        }
    }

    override fun show(notification: DesktopNotification): Boolean {
        val trayIcon = icon ?: return false
        return try {
            trayIcon.displayMessage(
                notification.title,
                notification.text,
                when (notification.level) {
                    DesktopNotification.Level.Info -> TrayIcon.MessageType.INFO
                    DesktopNotification.Level.Warning -> TrayIcon.MessageType.WARNING
                    DesktopNotification.Level.Error -> TrayIcon.MessageType.ERROR
                },
            )
            true
        } catch (e: Throwable) {
            xLogE("System tray: a notification could not be shown", e)
            false
        }
    }

    override fun status(text: String?) {
        tooltip = text?.let { "$APP_NAME – $it" } ?: APP_NAME
        try {
            icon?.toolTip = tooltip
        } catch (e: Throwable) {
            xLogE("System tray: the tooltip could not be set", e)
        }
    }

    private fun trayImage(): Image = DesktopTray::class.java.getResource(ICON_RESOURCE)
        ?.let { Toolkit.getDefaultToolkit().getImage(it) }
        // The packaged icon is missing (a test class path): a plain square, still something to click
        ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                color = java.awt.Color(0x4B, 0x5E, 0xC6)
                fillRect(0, 0, 16, 16)
                dispose()
            }
        }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    companion object {
        private const val APP_NAME = "Komikku"

        /** `desktopApp/icons/komikku.png`, copied into the resources by the build. */
        const val ICON_RESOURCE = "/app/komikku/desktop/komikku.png"
    }
}
