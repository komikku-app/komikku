package app.komikku.desktop.notification

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage

/**
 * The macOS menu-bar icon: a template image of the launcher icon's mark, the ring around the brush-drawn "こ" (ko),
 * drawn as a vector in black on transparent at [SIZE] x [SIZE] points, @1x and @2x. macOS uses only its alpha and tints
 * it for a light or dark menu bar and for the highlighted state; Windows and Linux keep the colour launcher icon.
 *
 * AWT marks tray images as templates only when [TEMPLATE_IMAGES_PROPERTY] is true when `sun.lwawt.macosx.CTrayIcon`
 * is initialised, which reads it once: in `SystemTray.add`, the first time an icon goes into the menu bar.
 */
internal object MacTrayIcon {
    const val TEMPLATE_IMAGES_PROPERTY = "apple.awt.enableTemplateImages"

    /** Points. AWT keeps this size while `TrayIcon.isImageAutoSize` is off (it only shrinks images taller than the bar). */
    const val SIZE = 18

    private const val RING_WIDTH = 1.5f
    private const val STROKE_WIDTH = 2f

    fun isMacOs(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        osName.lowercase().startsWith("mac")

    /** Sets [TEMPLATE_IMAGES_PROPERTY] unless it was given on the command line. */
    fun enableTemplateImages() {
        if (System.getProperty(TEMPLATE_IMAGES_PROPERTY) == null) System.setProperty(TEMPLATE_IMAGES_PROPERTY, "true")
    }

    val templateImagesEnabled: Boolean get() = System.getProperty(TEMPLATE_IMAGES_PROPERTY).toBoolean()

    /** @1x first: the native image takes its size in points from its first representation. */
    fun image(): BaseMultiResolutionImage = BaseMultiResolutionImage(render(1), render(2))

    /**
     * The glyph at [pixelsPerPoint], in the launcher mark's proportions: a short upper stroke, and a lower one that drops
     * from a hook on the left into a long flat stroke ending in a slight lift. The horizontal strokes and the hook are
     * centred on whole points so that, 2 points wide, they cover whole pixels at @1x and @2x.
     */
    fun render(pixelsPerPoint: Int): BufferedImage {
        val pixels = SIZE * pixelsPerPoint
        return BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
                scale(pixelsPerPoint.toDouble(), pixelsPerPoint.toDouble())
                color = Color.BLACK
                val center = SIZE / 2.0
                val radius = center - 1 - RING_WIDTH / 2
                stroke = BasicStroke(RING_WIDTH)
                draw(Ellipse2D.Double(center - radius, center - radius, 2 * radius, 2 * radius))
                stroke = BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                draw(
                    Path2D.Double().apply {
                        moveTo(7.0, 6.0)
                        lineTo(11.0, 6.0)
                    },
                )
                draw(
                    Path2D.Double().apply {
                        moveTo(6.0, 9.25)
                        curveTo(6.0, 11.25, 6.75, 12.0, 8.5, 12.0)
                        lineTo(11.25, 12.0)
                        lineTo(12.0, 11.25)
                    },
                )
                dispose()
            }
        }
    }
}
