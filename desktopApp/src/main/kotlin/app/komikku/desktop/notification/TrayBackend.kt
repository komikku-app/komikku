package app.komikku.desktop.notification

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * The AWT calls of [DesktopTray] (`SystemTray` / `TrayIcon`), so that its handling of the icons is tested with a fake:
 * a headless JVM cannot create a `TrayIcon` at all.
 */
internal interface TrayBackend {
    /** Whether there is a system tray. */
    fun isSupported(): Boolean

    /**
     * Puts an icon into the tray, with [menu] (`null`: none); [onAction] runs on a click on the icon (Windows: double
     * click) or on one of its notifications. Throws when it cannot.
     */
    fun add(image: Image, autoSize: Boolean, tooltip: String, menu: TrayMenu?, onAction: () -> Unit): Icon

    /** Runs [block] on the one thread all tray changes happen on (AWT's event thread) and waits for its result. */
    fun <T> serialized(block: () -> T): T

    interface Icon {
        fun displayMessage(title: String, text: String, level: DesktopNotification.Level)

        fun setToolTip(text: String)

        fun remove()
    }
}

/** The tray icon's menu: [openText] runs [onOpen], [exitText] runs [onExit]. */
internal class TrayMenu(val openText: String, val exitText: String, val onOpen: () -> Unit, val onExit: () -> Unit)

/** Runs a task once after a delay; returns what cancels it. */
internal fun interface TrayTimer {
    fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit
}

/** [TrayTimer] on one daemon thread, started when first used. */
internal object DaemonTrayTimer : TrayTimer {
    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { Thread(it, "komikku-tray-timer").apply { isDaemon = true } }
    }

    override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
        val future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
        return { future.cancel(false) }
    }
}

internal class AwtTrayBackend(private val traySupported: () -> Boolean) : TrayBackend {

    override fun isSupported(): Boolean = traySupported()

    override fun add(
        image: Image,
        autoSize: Boolean,
        tooltip: String,
        menu: TrayMenu?,
        onAction: () -> Unit,
    ): TrayBackend.Icon {
        val popup = menu?.let {
            PopupMenu().apply {
                add(MenuItem(it.openText).apply { addActionListener { _ -> it.onOpen() } })
                addSeparator()
                add(MenuItem(it.exitText).apply { addActionListener { _ -> it.onExit() } })
            }
        }
        val trayIcon = TrayIcon(image, tooltip, popup).apply {
            isImageAutoSize = autoSize
            addActionListener { onAction() }
        }
        SystemTray.getSystemTray().add(trayIcon)
        return AwtIcon(trayIcon)
    }

    override fun <T> serialized(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private class AwtIcon(private val trayIcon: TrayIcon) : TrayBackend.Icon {
        override fun displayMessage(title: String, text: String, level: DesktopNotification.Level) {
            trayIcon.displayMessage(
                title,
                text,
                when (level) {
                    DesktopNotification.Level.Info -> TrayIcon.MessageType.INFO
                    DesktopNotification.Level.Warning -> TrayIcon.MessageType.WARNING
                    DesktopNotification.Level.Error -> TrayIcon.MessageType.ERROR
                },
            )
        }

        override fun setToolTip(text: String) {
            trayIcon.toolTip = text
        }

        override fun remove() {
            SystemTray.getSystemTray().remove(trayIcon)
        }
    }
}
