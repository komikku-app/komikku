package app.komikku.desktop.window

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit

/**
 * The screens' usable areas: each screen without its menu bar, Dock or task bar, in AWT user-space units (what a
 * window's dp are; Windows' 150 % scaling makes a 1920x1080 screen 1280x720). The main screen comes first; empty when
 * headless.
 */
object DesktopScreens {

    fun usableAreas(): List<Rectangle> = runCatching {
        if (GraphicsEnvironment.isHeadless()) return@runCatching emptyList()
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val main = environment.defaultScreenDevice
        val toolkit = Toolkit.getDefaultToolkit()
        (listOf(main) + environment.screenDevices.filter { it != main }).map { device ->
            val configuration = device.defaultConfiguration
            val bounds = configuration.bounds
            val insets = toolkit.getScreenInsets(configuration)
            Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom,
            )
        }
    }.getOrDefault(emptyList())
}
