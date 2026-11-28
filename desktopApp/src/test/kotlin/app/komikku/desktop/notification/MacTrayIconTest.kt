package app.komikku.desktop.notification

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import kotlin.math.abs

/** The macOS menu-bar template image and which tray image each OS gets. */
class MacTrayIconTest {

    private var savedProperty: String? = null

    @BeforeEach
    fun clearProperty() {
        savedProperty = System.getProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY)
        System.clearProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY)
    }

    @AfterEach
    fun restoreProperty() {
        savedProperty?.let { System.setProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY, it) }
            ?: System.clearProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY)
    }

    @Test
    fun `macOS is told apart from Windows and Linux by its os name`() {
        MacTrayIcon.isMacOs("Mac OS X") shouldBe true
        MacTrayIcon.isMacOs("Windows 11") shouldBe false
        MacTrayIcon.isMacOs("Linux") shouldBe false
        MacTrayIcon.isMacOs("") shouldBe false
    }

    @Test
    fun `the template image is black on transparent, alpha only, with a transparent one point margin`() {
        for (scale in listOf(1, 2)) {
            val image = MacTrayIcon.render(scale)
            val size = MacTrayIcon.SIZE * scale
            image.width shouldBe size
            image.height shouldBe size
            var ink = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val argb = image.getRGB(x, y)
                    (argb and 0xFFFFFF) shouldBe 0
                    val alpha = argb ushr 24
                    ink += alpha
                    if (x < scale || y < scale || x >= size - scale || y >= size - scale) alpha shouldBe 0
                }
            }
            // Covered area in whole pixels: at 1x the 1.5 point ring has no fully opaque pixel, so alpha is summed
            (ink / 255) shouldBeGreaterThan size * size / 5
            (ink / 255) shouldBeLessThan size * size * 2 / 5
        }
    }

    @Test
    fun `the horizontal strokes cover whole pixels at 1x and 2x`() {
        for (scale in listOf(1, 2)) {
            val image = MacTrayIcon.render(scale)
            // Upper stroke from y = 5 to 7 points, lower stroke from y = 11 to 13 points, both straight at x = 9 points
            val column = 9 * scale
            for ((top, bottom) in listOf(5 to 7, 11 to 13)) {
                image.alpha(column, top * scale - 1) shouldBe 0
                for (y in top * scale until bottom * scale) image.alpha(column, y) shouldBe 255
                image.alpha(column, bottom * scale) shouldBe 0
            }
        }
    }

    @Test
    fun `the 2x image is the 1x glyph drawn at twice the resolution`() {
        val small = MacTrayIcon.render(1)
        val large = MacTrayIcon.render(2)
        var totalDifference = 0
        for (y in 0 until MacTrayIcon.SIZE) {
            for (x in 0 until MacTrayIcon.SIZE) {
                val downscaled = (
                    large.alpha(2 * x, 2 * y) + large.alpha(2 * x + 1, 2 * y) +
                        large.alpha(2 * x, 2 * y + 1) + large.alpha(2 * x + 1, 2 * y + 1)
                    ) / 4
                val difference = abs(downscaled - small.alpha(x, y))
                difference shouldBeLessThan 64
                totalDifference += difference
            }
        }
        totalDifference / (MacTrayIcon.SIZE * MacTrayIcon.SIZE) shouldBeLessThan 8
    }

    @Test
    fun `the tray image has an 18 point 1x variant first and a 2x variant`() {
        val image = MacTrayIcon.image()
        image.getWidth(null) shouldBe MacTrayIcon.SIZE
        image.resolutionVariants.map { it.getWidth(null) to it.getHeight(null) } shouldContainExactly
            listOf(18 to 18, 36 to 36)
    }

    @Test
    fun `the property is set unless given on the command line`() {
        MacTrayIcon.enableTemplateImages()
        System.getProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY) shouldBe "true"
        MacTrayIcon.templateImagesEnabled shouldBe true

        System.setProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY, "false")
        MacTrayIcon.enableTemplateImages()
        System.getProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY) shouldBe "false"
        MacTrayIcon.templateImagesEnabled shouldBe false
    }

    @Test
    fun `on macOS installing sets the property before any tray icon exists and uses the template image`() {
        // No tray: install stops before creating a TrayIcon, so the property was set before one could be
        val tray = DesktopTray(traySupported = { false }, macOs = true)
        tray.install("Open", "Exit", onOpen = {}, onExit = {}) shouldBe false
        System.getProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY) shouldBe "true"
        tray.usesTemplateImage() shouldBe true
        tray.trayImage().shouldBeInstanceOf<BaseMultiResolutionImage>()
            .resolutionVariants.map { it.getWidth(null) } shouldContainExactly listOf(18, 36)
    }

    @Test
    fun `Windows and Linux keep the colour icon and leave the property alone`() {
        val tray = DesktopTray(traySupported = { false }, macOs = false)
        tray.install("Open", "Exit", onOpen = {}, onExit = {}) shouldBe false
        System.getProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY) shouldBe null
        System.setProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY, "true")
        tray.usesTemplateImage() shouldBe false
        (tray.trayImage() is BaseMultiResolutionImage) shouldBe false
    }

    @Test
    fun `macOS falls back to the colour icon when template images were switched off on the command line`() {
        System.setProperty(MacTrayIcon.TEMPLATE_IMAGES_PROPERTY, "false")
        val tray = DesktopTray(traySupported = { false }, macOs = true)
        tray.install("Open", "Exit", onOpen = {}, onExit = {}) shouldBe false
        tray.usesTemplateImage() shouldBe false
        (tray.trayImage() is BaseMultiResolutionImage) shouldBe false
    }

    private fun BufferedImage.alpha(x: Int, y: Int): Int = getRGB(x, y) ushr 24
}
