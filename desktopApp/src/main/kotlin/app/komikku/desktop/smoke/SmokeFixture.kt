package app.komikku.desktop.smoke

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import javax.imageio.ImageIO

/**
 * The UI smoke mode's manga, generated into the local source's folder (no binary in git): `Smoke Fixture & Co/` with
 * a cover and [CHAPTERS] chapter folders of [PAGES] pages each, 600 × 900, a large page number and a colour per page
 * so the page turn shows in the screenshots. Every file carries the same fixed time ([FILE_TIME]), so the dates the
 * app shows (chapter upload dates come from the files) are the same in every run.
 */
internal object SmokeFixture {

    const val TITLE = "Smoke Fixture & Co"
    val CHAPTERS = listOf("Chapter 1", "Chapter 2")
    const val PAGES = 3

    val FILE_TIME: Instant = Instant.parse("2024-01-01T12:00:00Z")

    private const val WIDTH = 600
    private const val HEIGHT = 900

    private val PAGE_COLOURS = listOf(Color(0xE5, 0x39, 0x35), Color(0x1E, 0x88, 0xE5), Color(0x43, 0xA0, 0x47))
    private val COVER_COLOUR = Color(0x5E, 0x35, 0xB1)

    /** Writes the fixture into [localSourceFolder]; returns the manga's folder. */
    fun write(localSourceFolder: File): File {
        val manga = File(localSourceFolder, TITLE).apply { mkdirs() }
        writePng(File(manga, "cover.png"), COVER_COLOUR, "Smoke")
        CHAPTERS.forEachIndexed { chapterIndex, chapter ->
            val folder = File(manga, chapter).apply { mkdirs() }
            repeat(PAGES) { page ->
                val colour = PAGE_COLOURS[(chapterIndex + page) % PAGE_COLOURS.size]
                writePng(File(folder, "%03d.png".format(page + 1)), colour, "${chapterIndex + 1}-${page + 1}")
            }
        }
        // Children first, so writing a file does not move its folder's time again
        manga.walkBottomUp().forEach { Files.setLastModifiedTime(it.toPath(), FileTime.from(FILE_TIME)) }
        return manga
    }

    private fun writePng(file: File, background: Color, label: String) {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.color = background
            graphics.fillRect(0, 0, WIDTH, HEIGHT)
            graphics.color = Color.WHITE
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 160)
            val metrics = graphics.fontMetrics
            graphics.drawString(label, (WIDTH - metrics.stringWidth(label)) / 2, (HEIGHT + metrics.ascent) / 2 - metrics.descent)
        } finally {
            graphics.dispose()
        }
        check(ImageIO.write(image, "png", file)) { "No PNG writer for $file" }
    }
}
