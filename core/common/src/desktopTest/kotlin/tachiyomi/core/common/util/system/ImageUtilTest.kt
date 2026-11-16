package tachiyomi.core.common.util.system

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class ImageUtilTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `detects image types from magic bytes`() {
        val samples = mapOf(
            bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10) to ImageUtil.ImageType.JPEG,
            bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) to ImageUtil.ImageType.PNG,
            ascii("GIF89a....") to ImageUtil.ImageType.GIF,
            ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ") to ImageUtil.ImageType.WEBP,
            bytes(0xFF, 0x0A, 0x00) to ImageUtil.ImageType.JXL,
            bytes(0, 0, 0, 0x1C) + ascii("ftypavif\u0000\u0000\u0000\u0000avifmif1") to ImageUtil.ImageType.AVIF,
            bytes(0, 0, 0, 0x18) + ascii("ftypheic\u0000\u0000\u0000\u0000mif1heic") to ImageUtil.ImageType.HEIF,
        )
        samples.forEach { (header, type) ->
            ImageUtil.findImageType(header.inputStream()) shouldBe type
        }
        ImageUtil.findImageType(ascii("not an image").inputStream()) shouldBe null
        ImageUtil.findImageType(ByteArray(0).inputStream()) shouldBe null
    }

    @Test
    fun `isImage and mime helpers`() {
        ImageUtil.isImage("001.jpg") shouldBe true
        ImageUtil.isImage("page.cbi") shouldBe true
        ImageUtil.isImage("ComicInfo.xml") shouldBe false
        ImageUtil.isImage("noext") { bytes(0x89, 0x50, 0x4E, 0x47).inputStream() } shouldBe true
        ImageUtil.getExtensionFromMimeType("image/webp") { ByteArray(0).inputStream() } shouldBe "webp"
        ImageUtil.getExtensionFromMimeType(null) { bytes(0xFF, 0xD8, 0xFF).inputStream() } shouldBe "jpg"
    }

    @Test
    fun `animated formats`() {
        val animatedWebp = ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8X\u0000\u0000\u0000\u0000") + bytes(0x02, 0, 0, 0)
        ImageUtil.isAnimatedAndSupported(Buffer().write(animatedWebp)) shouldBe true
        ImageUtil.isAnimatedAndSupported(Buffer().write(ascii("GIF89a"))) shouldBe true
        ImageUtil.isAnimatedAndSupported(Buffer().write(bytes(0xFF, 0xD8, 0xFF))) shouldBe false
    }

    @Test
    fun `splits tall images into jpeg parts like Android and leaves other images alone`(@TempDir dir: File) {
        fun png(width: Int, height: Int) = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val tmpDir = UniFile.fromFile(dir)!!

        // 10 000 px tall: headless optimal height = 2 × 2160 → 3 parts, original deleted
        dir.resolve("001.png").writeBytes(png(100, 10_000))
        ImageUtil.splitTallImage(tmpDir, tmpDir.findFile("001.png")!!, "001") shouldBe true
        val parts = dir.list()!!.sorted()
        parts shouldBe listOf("001__001.jpg", "001__002.jpg", "001__003.jpg")
        parts.sumOf { ImageIO.read(dir.resolve(it)).height } shouldBe 10_000
        parts.forEach { ImageIO.read(dir.resolve(it)).width shouldBe 100 }

        // Not tall: unchanged
        dir.resolve("002.png").writeBytes(png(100, 300))
        ImageUtil.splitTallImage(tmpDir, tmpDir.findFile("002.png")!!, "002") shouldBe true
        dir.resolve("002.png").exists() shouldBe true
    }
}
