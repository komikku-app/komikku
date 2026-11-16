package app.komikku.desktop.platform

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI

/** KMK: opens URLs in the system browser (`java.awt.Desktop.browse`); `false` when there is none. */
object DesktopBrowser {
    fun open(url: String): Boolean = try {
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Could not open $url" }
        false
    }
}
