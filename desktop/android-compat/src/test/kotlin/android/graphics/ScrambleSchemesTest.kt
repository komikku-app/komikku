package android.graphics

import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.floor

/**
 * Four image scrambling schemes of Keiyoushi extensions, end to end on the compat layer: a generated page is
 * scrambled here, then unscrambled by the extension's algorithm (re-stated below from its behaviour; no extension code
 * or images in the repository) and compared with the page. The unscrambled bitmap must equal the page bit for bit; the
 * JPEG the interceptor returns must be within JPEG's error of it (under 2 levels on average). Image sizes are not
 * multiples of the block sizes, so the edge handling is checked too.
 */
class ScrambleSchemesTest {

    private val width = 413
    private val height = 587
    private val page = TestImages.gradient(width, height)

    /**
     * GigaViewer (`lib-multisrc/gigaviewer`: Shonen Jump+, Comic Days, Tonari no Young Jump, …; URL fragment
     * `#scramble`): a 4 x 4 grid of blocks whose sides are multiples of 8 is transposed; the edge outside the grid
     * stays in place. Unscrambling draws the whole image, then each block back.
     */
    @Test
    fun `GigaViewer block transpose`() {
        val blockWidth = (floor(width / 32.0) * 8).toInt()
        val blockHeight = (floor(height / 32.0) * 8).toInt()
        val scrambled = page.copyOf()
        for (e in 0 until 16) {
            val (sx, sy) = (e % 4) * blockWidth to (e / 4) * blockHeight
            val (dx, dy) = (e / 4) * blockWidth to (e % 4) * blockHeight
            copyRect(page, scrambled, dx, dy, sx, sy, blockWidth, blockHeight)
        }

        check(png(scrambled)) { image ->
            val result = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val srcRect = Rect()
            val dstRect = Rect()
            canvas.drawBitmap(image, 0f, 0f, null)
            for (e in 0 until 16) {
                val dstBlockIndex = (e % 4) * 4 + e / 4
                val srcX = (e % 4) * blockWidth
                val srcY = (e / 4) * blockHeight
                val dstX = (dstBlockIndex % 4) * blockWidth
                val dstY = (dstBlockIndex / 4) * blockHeight
                srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
                dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)
                canvas.drawBitmap(image, srcRect, dstRect, null)
            }
            result
        }
    }

    /**
     * Comici viewer (`lib-multisrc/comiciviewer`; URL fragment `#scramble=[…]`): a 4 x 4 grid of equal tiles, numbered
     * down the columns, is permuted; tile `d` of the page is tile `tiles[d]` of the image. The right and bottom edges
     * outside the grid stay in place.
     */
    @Test
    fun `Comici tile permutation`() {
        val tiles = listOf(5, 14, 0, 9, 3, 12, 7, 1, 15, 10, 2, 6, 11, 4, 13, 8)
        val tileWidth = width / 4
        val tileHeight = height / 4
        val scrambled = page.copyOf()
        tiles.forEachIndexed { dest, source ->
            copyRect(page, scrambled, (dest / 4) * tileWidth, (dest % 4) * tileHeight, (source / 4) * tileWidth, (source % 4) * tileHeight, tileWidth, tileHeight)
        }

        check(jpegQuality(scrambled), exactBitmap = false) { image ->
            val result = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val srcRect = Rect()
            val dstRect = Rect()
            for (dest in tiles.indices) {
                val destX = (dest / 4) * tileWidth
                val destY = (dest % 4) * tileHeight
                dstRect.set(destX, destY, destX + tileWidth, destY + tileHeight)
                val sourceX = (tiles[dest] / 4) * tileWidth
                val sourceY = (tiles[dest] % 4) * tileHeight
                srcRect.set(sourceX, sourceY, sourceX + tileWidth, sourceY + tileHeight)
                canvas.drawBitmap(image, srcRect, dstRect, null)
            }
            if (tileWidth * 4 < image.getWidth()) {
                srcRect.set(tileWidth * 4, 0, image.getWidth(), image.getHeight())
                canvas.drawBitmap(image, srcRect, srcRect, null)
            }
            if (tileHeight * 4 < image.getHeight()) {
                srcRect.set(0, tileHeight * 4, tileWidth * 4, image.getHeight())
                canvas.drawBitmap(image, srcRect, srcRect, null)
            }
            result
        }
        // The same scheme on a lossless input: bit for bit
        check(png(scrambled)) { image -> unscrambleComici(image, tiles, tileWidth, tileHeight) }
    }

    private fun unscrambleComici(image: Bitmap, tiles: List<Int>, tileWidth: Int, tileHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        for (dest in tiles.indices) {
            val src = Rect((tiles[dest] / 4) * tileWidth, (tiles[dest] % 4) * tileHeight, 0, 0)
            src.set(src.left, src.top, src.left + tileWidth, src.top + tileHeight)
            val dst = Rect((dest / 4) * tileWidth, (dest % 4) * tileHeight, 0, 0)
            dst.set(dst.left, dst.top, dst.left + tileWidth, dst.top + tileHeight)
            canvas.drawBitmap(image, src, dst, null)
        }
        canvas.drawBitmap(image, Rect(tileWidth * 4, 0, width, height), Rect(tileWidth * 4, 0, width, height), null)
        canvas.drawBitmap(image, Rect(0, tileHeight * 4, tileWidth * 4, height), Rect(0, tileHeight * 4, tileWidth * 4, height), null)
        return result
    }

    /**
     * JMComic (`zh.jinmantiantang`, albums from id 220980): the page is cut into an even number of horizontal stripes
     * (from the MD5 of album id and image name) stacked in reverse order; the remainder of the height goes with the
     * first stripe of the page.
     */
    @Test
    fun `JMComic stripe reversal`() {
        val rows = jmRows(aid = 421_927, imageIndex = "00007")
        val scrambled = IntArray(page.size)
        stripes(rows).forEach { (pageY, imageY, stripeHeight) ->
            copyRect(page, scrambled, 0, pageY, 0, imageY, width, stripeHeight)
        }

        check(png(scrambled)) { image ->
            val h = image.getHeight()
            val w = image.getWidth()
            val remainder = h % rows
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            for (x in 0 until rows) {
                var copyH = floor(h / rows.toDouble()).toInt()
                var py = copyH * x
                val y = h - (copyH * (x + 1)) - remainder
                if (x == 0) copyH += remainder else py += remainder
                canvas.drawBitmap(image, Rect(0, y, w, y + copyH), Rect(0, py, w, py + copyH), null)
            }
            result
        }
    }

    /** JMComic's stripe count: 2 × (a hex digit of the MD5's last byte, as a character code, mod 8 or 10) + 2. */
    private fun jmRows(aid: Int, imageIndex: String): Int {
        val modulus = if (aid >= 421_926) 8 else 10
        val lastByte = MessageDigest.getInstance("MD5").digest("$aid$imageIndex".toByteArray()).last().toInt() and 0xff
        return 2 * (lastByte.toString(16).last().code % modulus) + 2
    }

    /** (page y, image y, height) of each stripe. */
    private fun stripes(rows: Int): List<Triple<Int, Int, Int>> {
        val base = height / rows
        val remainder = height % rows
        return (0 until rows).map { x ->
            val stripeHeight = if (x == 0) base + remainder else base
            val pageY = if (x == 0) 0 else base * x + remainder
            Triple(pageY, height - base * (x + 1) - remainder, stripeHeight)
        }
    }

    /**
     * HanabiManga (`zh.hanabimanga`; key in the URL fragment): a grid of `cols` x `rows` tiles (integer fractions of
     * the size) is permuted pixel array to pixel array: the image is decoded mutable, read with `getPixels`, the tiles
     * copied back in place, written with `setPixels` and returned as PNG. The site derives the permutation from an
     * encrypted ticket; a seeded shuffle stands in for it here (the pixel handling is what the compat layer does).
     */
    @Test
    fun `HanabiManga pixel tile permutation`() {
        val cols = 7
        val rows = 9
        val w = cols * 59
        val h = rows * 65
        val original = TestImages.pattern(w, h)
        val destFromSource = (0 until cols * rows).shuffled(kotlin.random.Random(20261128)).toIntArray()
        val scrambled = IntArray(original.size)
        destFromSource.forEachIndexed { dest, source ->
            val st = tile(source, w, h, cols, rows)
            val dt = tile(dest, w, h, cols, rows)
            copyRect(original, scrambled, dt[0], dt[1], st[0], st[1], st[2], st[3], w)
        }
        val png = TestImages.png(scrambled, w, h)

        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, BitmapFactory.Options().apply { inMutable = true })!!
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)
        destFromSource.forEachIndexed { dest, source ->
            val st = tile(source, w, h, cols, rows)
            val dt = tile(dest, w, h, cols, rows)
            for (y in 0 until minOf(st[3], dt[3])) {
                val srcOff = (st[1] + y) * w + st[0]
                src.copyInto(dst, (dt[1] + y) * w + dt[0], srcOff, srcOff + minOf(st[2], dt[2]))
            }
        }
        bitmap.setPixels(dst, 0, w, 0, 0, w, h)
        val out = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()

        TestImages.pixelsOf(TestImages.readImageIo(out)) shouldBe original
    }

    /** x, y, width, height of tile [index] (row-major). */
    private fun tile(index: Int, w: Int, h: Int, cols: Int, rows: Int): IntArray {
        val col = index % cols
        val row = index / cols
        val x0 = col * w / cols
        val y0 = row * h / rows
        return intArrayOf(x0, y0, (col + 1) * w / cols - x0, (row + 1) * h / rows - y0)
    }

    private fun png(pixels: IntArray) = TestImages.png(pixels, width, height)

    private fun jpegQuality(pixels: IntArray) = TestImages.jpeg(pixels, width, height, 0.98f)

    /**
     * Runs an interceptor's work on [encoded] like the extensions do (decode, [unscramble], JPEG at 90) and compares:
     * the unscrambled bitmap with the page (exactly when the input was lossless) and the JPEG with the page.
     */
    private fun check(encoded: ByteArray, exactBitmap: Boolean = true, unscramble: (Bitmap) -> Bitmap) {
        val image = BitmapFactory.decodeStream(ByteArrayInputStream(encoded))!!
        val result = unscramble(image)
        image.recycle()
        val pixels = with(TestImages) { result.argb() }
        if (exactBitmap) {
            pixels shouldBe page
        } else {
            withClue("unscrambled from a JPEG: mean difference") { TestImages.meanChannelDifference(pixels, page) shouldBeLessThan 2.0 }
        }
        val jpeg = ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        result.recycle()
        val shown = TestImages.pixelsOf(TestImages.readImageIo(jpeg))
        withClue("the returned JPEG: mean difference") { TestImages.meanChannelDifference(shown, page) shouldBeLessThan 2.0 }
    }

    private fun copyRect(from: IntArray, to: IntArray, fromX: Int, fromY: Int, toX: Int, toY: Int, w: Int, h: Int, stride: Int = width) {
        for (y in 0 until h) from.copyInto(to, (toY + y) * stride + toX, (fromY + y) * stride + fromX, (fromY + y) * stride + fromX + w)
    }
}
