package app.komikku.desktop.notification

import exh.log.xLogE
import exh.log.xLogW
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.concurrent.thread

/** P17: the desktop's tray setting (desktop-only key; on by default where the desktop has a tray). */
class DesktopTrayPreferences(private val preferenceStore: PreferenceStore) {
    fun trayIcon() = preferenceStore.getBoolean("desktop_tray_icon", true)

    /** M2 review fix: whether system notifications are posted at all, on every OS and backend (on by default). */
    fun systemNotifications() = preferenceStore.getBoolean("desktop_system_notifications", true)
}

/**
 * P17: the optional system tray icon and the notifications shown through it (AWT `SystemTray` / `TrayIcon`; Compose's
 * `Tray` is the same API bound to a composition). Without a tray — `SystemTray.isSupported()` false (e.g. GNOME
 * without an AppIndicator extension) or a headless environment — nothing is installed and [show] returns `false`: the
 * app works the same, without system notifications (its own banners and snackbars stay). Any AWT failure is logged and
 * counts as "no tray".
 *
 * The icon opens the window (click, or "Open Komikku" in its menu) and offers "Exit"; clicking a notification opens the
 * window too (where the platform reports such clicks). Its tooltip shows [status] (the library update's progress).
 * On macOS the icon is a monochrome template image ([MacTrayIcon]) that follows a light or dark menu bar; elsewhere it
 * is the colour launcher icon.
 *
 * ANSWER 011 (b): AWT shows a notification only through an icon in the tray. With the tray setting off ([install] not
 * called, or [remove]d), [show] puts a temporary icon into the tray (no menu; a click opens the window once [install]
 * has given that action) and takes it out [TEMPORARY_ICON_MILLIS] after the last notification; a notification in that
 * time reuses it. [install] takes it out before adding the user's icon, so there are never two. Not on macOS, where
 * notifications go through [MacOsNotifier].
 *
 * Every change of the icons happens on AWT's event thread ([TrayBackend.serialized]), so there is no lock that the
 * window's thread and a notifying thread could wait on in turn.
 */
class DesktopTray internal constructor(
    private val macOs: Boolean,
    private val temporaryIcons: Boolean,
    private val backend: TrayBackend,
    private val timer: TrayTimer,
    /** Called once with [quit] when the first temporary icon is added: the app's exit must take it out. */
    private val hookExit: (quit: () -> Unit) -> Unit,
) : DesktopNotifier {

    /** [traySupported] and [macOs] are injected for tests (a headless test cannot create a `TrayIcon` at all). */
    constructor(
        traySupported: () -> Boolean = { !GraphicsEnvironment.isHeadless() && SystemTray.isSupported() },
        macOs: Boolean = MacTrayIcon.isMacOs(),
    ) : this(macOs, !macOs, AwtTrayBackend(traySupported), DaemonTrayTimer, ::quitOnJvmExit)

    /** The user's icon (the tray setting); only changed on [TrayBackend.serialized]. */
    @Volatile
    private var icon: TrayBackend.Icon? = null

    /** The icon shown for notifications while the user's is off. */
    @Volatile
    private var temporary: TrayBackend.Icon? = null

    private var cancelRemoval: (() -> Unit)? = null

    /** Counts the temporary icon's holds: a removal that was overtaken by a later notification does nothing. */
    private var hold = 0

    private var exitHooked = false

    private var quitting = false

    @Volatile
    private var openAction: () -> Unit = {}

    @Volatile
    private var tooltip: String = APP_NAME

    /** Whether the user's icon is in the tray now. */
    val isInstalled: Boolean get() = icon != null

    /** Whether a temporary icon is in the tray now. */
    internal val hasTemporaryIcon: Boolean get() = temporary != null

    /**
     * Puts the icon into the tray (when there is one); [openText] / [exitText] are its menu's labels, [onOpen] shows the
     * window, [onExit] quits. Does nothing when already installed. Returns whether the icon is there.
     */
    fun install(openText: String, exitText: String, onOpen: () -> Unit, onExit: () -> Unit): Boolean =
        serialized(false) {
            openAction = onOpen
            if (icon != null) return@serialized true
            if (quitting) return@serialized false
            removeTemporary()
            // Before the first `SystemTray.add`, where AWT's macOS tray class reads it once
            if (macOs) MacTrayIcon.enableTemplateImages()
            if (!supported()) return@serialized false
            icon = addIcon(TrayMenu(openText, exitText, onOpen, onExit), onOpen, "the icon could not be added")
            icon != null
        }

    /** Takes the icons out of the tray (the setting was switched off, the window is gone). */
    fun remove() {
        serialized(Unit) { removeAll() }
    }

    /** The app exits: takes the icons out of the tray and adds none after that. */
    fun quit() {
        serialized(Unit) {
            quitting = true
            removeAll()
        }
    }

    override fun show(notification: DesktopNotification): Boolean {
        if (icon == null && !temporaryIcons) return false
        return serialized(false) {
            val target = icon ?: temporaryIcon() ?: return@serialized false
            try {
                target.displayMessage(notification.title, notification.text, notification.level)
                true
            } catch (e: Throwable) {
                xLogE("System tray: a notification could not be shown", e)
                false
            }
        }
    }

    override fun status(text: String?) {
        tooltip = text?.let { "$APP_NAME – $it" } ?: APP_NAME
        if (icon == null && temporary == null) return
        serialized(Unit) {
            try {
                icon?.setToolTip(tooltip)
                temporary?.setToolTip(tooltip)
            } catch (e: Throwable) {
                xLogE("System tray: the tooltip could not be set", e)
            }
        }
    }

    /** The temporary icon, added when there is none, with its removal (re)scheduled; `null` when there can be none. */
    private fun temporaryIcon(): TrayBackend.Icon? {
        if (!temporaryIcons || quitting) return null
        val current = temporary
            ?: (if (supported()) addIcon(null, { openAction() }, "a temporary icon could not be added") else null)
            ?: return null
        if (temporary == null) {
            temporary = current
            if (!exitHooked) {
                exitHooked = true
                hookExit(::quit)
            }
        }
        cancelRemoval?.invoke()
        val thisHold = ++hold
        cancelRemoval = timer.schedule(TEMPORARY_ICON_MILLIS) {
            serialized(Unit) { if (hold == thisHold) removeTemporary() }
        }
        return current
    }

    private fun removeAll() {
        removeTemporary()
        val trayIcon = icon ?: return
        icon = null
        removeIcon(trayIcon, "the icon could not be removed")
    }

    private fun removeTemporary() {
        cancelRemoval?.invoke()
        cancelRemoval = null
        hold++
        val trayIcon = temporary ?: return
        temporary = null
        removeIcon(trayIcon, "the temporary icon could not be removed")
    }

    private fun supported(): Boolean = try {
        backend.isSupported()
    } catch (e: Throwable) {
        xLogW("System tray: not available ($e)")
        false
    }

    private fun addIcon(menu: TrayMenu?, onAction: () -> Unit, failure: String): TrayBackend.Icon? = try {
        val template = usesTemplateImage()
        // The template image keeps its 18 points: auto-size would stretch it to the bar's height and blur it
        backend.add(trayImage(template), autoSize = !template, tooltip, menu, onAction)
    } catch (e: Throwable) {
        xLogE("System tray: $failure", e)
        null
    }

    private fun removeIcon(trayIcon: TrayBackend.Icon, failure: String) {
        try {
            trayIcon.remove()
        } catch (e: Throwable) {
            xLogE("System tray: $failure", e)
        }
    }

    private fun <T> serialized(failed: T, block: () -> T): T = try {
        backend.serialized(block)
    } catch (e: Throwable) {
        xLogE("System tray: AWT's event thread failed", e)
        failed
    }

    /**
     * Whether the icon is the macOS template image: not when [MacTrayIcon.TEMPLATE_IMAGES_PROPERTY] was set to false on
     * the command line, as macOS would then draw the black glyph untinted, unreadable on a dark menu bar.
     */
    internal fun usesTemplateImage(): Boolean = macOs && MacTrayIcon.templateImagesEnabled

    internal fun trayImage(template: Boolean = usesTemplateImage()): Image =
        if (template) MacTrayIcon.image() else colourImage()

    private fun colourImage(): Image = DesktopTray::class.java.getResource(ICON_RESOURCE)
        ?.let { Toolkit.getDefaultToolkit().getImage(it) }
        // The packaged icon is missing (a test class path): a plain square, still something to click
        ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                color = java.awt.Color(0x4B, 0x5E, 0xC6)
                fillRect(0, 0, 16, 16)
                dispose()
            }
        }

    companion object {
        private const val APP_NAME = "Komikku"

        /** `desktopApp/icons/komikku.png`, copied into the resources by the build. */
        const val ICON_RESOURCE = "/app/komikku/desktop/komikku.png"

        /**
         * How long the temporary icon stays after the last notification [guess: Windows shows a toast about 5 s, and
         * taking the icon away may take its toast with it, so not earlier].
         */
        const val TEMPORARY_ICON_MILLIS = 10_000L

        /** How long the JVM's exit waits for AWT to take the temporary icon out. */
        private const val QUIT_WAIT_MILLIS = 2_000L

        /** The temporary icon must not outlive the process (Windows would leave it in the tray until hovered). */
        private fun quitOnJvmExit(quit: () -> Unit) {
            val hook = Thread { thread(isDaemon = true, name = "komikku-tray-quit") { quit() }.join(QUIT_WAIT_MILLIS) }
            Runtime.getRuntime().addShutdownHook(hook.apply { name = "komikku-tray-exit" })
        }
    }
}
