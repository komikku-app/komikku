package app.komikku.desktop.platform

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI

/** KMK: opens URLs in the system browser (`java.awt.Desktop.browse`) and folders; `false` when there is none. */
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

    /** Opens [folder] in the system file manager (`java.awt.Desktop.open`, P12); `false` when that is not possible. */
    fun openFolder(folder: File): Boolean = try {
        folder.mkdirs()
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(folder)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Could not open $folder" }
        false
    }
}
