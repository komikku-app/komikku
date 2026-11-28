package app.komikku.desktop.selftest

import app.komikku.desktop.extension.LoadedExtension
import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * KMK: the self-test's check that a real extension unscrambles a page on the compat layer's `android.graphics`
 * (ATLAS_INSTRUCTIONS.md ANSWER 016, "done means seen"). A generated page is scrambled the way the extension's site
 * scrambles it, served by a loopback HTTP server (no network), fetched through the extension's own client with the
 * URL its page list makes, so its interceptor runs; the image that comes back is decoded and compared with the page.
 *
 * The comparison is by pixels, not by a hash of the returned file: the extension re-encodes the page as JPEG, whose
 * bytes may differ between JPEG builds while the picture is the same. The page is a smooth gradient that tells every
 * place apart, so JPEG keeps it under 2 levels on average while a block out of place costs far more (the scrambled
 * page is over 20 away). The report line names both SHA-256 for reference.
 */
object ScrambledPageCheck {

    /** How a site scrambles its pages, and the URL fragment that tells the extension's interceptor to undo it. */
    class Scheme(val name: String, val fragment: String, val scramble: (IntArray, Int, Int) -> IntArray)

    /**
     * GigaViewer (Shonen Jump+, Comic Days, Tonari no Young Jump, … in `lib-multisrc/gigaviewer`): a 4 x 4 grid of
     * blocks, their sides `floor(size / 32) * 8`, is transposed; what lies outside the grid stays in place. A
     * transposition is its own inverse, so this both scrambles and unscrambles.
     */
    val GIGAVIEWER = Scheme("GigaViewer", "scramble") { page, width, height ->
        val blockWidth = width / 32 * 8
        val blockHeight = height / 32 * 8
        val out = page.copyOf()
        for (bx in 0 until 4) {
            for (by in 0 until 4) {
                for (y in 0 until blockHeight) {
                    val from = (by * blockHeight + y) * width + bx * blockWidth
                    page.copyInto(out, (bx * blockHeight + y) * width + by * blockWidth, from, from + blockWidth)
                }
            }
        }
        out
    }

    /** The extensions of `.github/desktop-test-extensions.txt` whose pages are checked, by package. */
    val schemes: Map<String, Scheme> = mapOf("eu.kanade.tachiyomi.extension.ja.shonenjumpplus" to GIGAVIEWER)

    const val WIDTH = 600
    const val HEIGHT = 851

    /** Under 2 levels on average for JPEG; a scrambled page is over [SCRAMBLED_AT_LEAST]. */
    const val TOLERANCE = 2.0
    const val SCRAMBLED_AT_LEAST = 20.0

    /** The page check of [loaded] (with its first HTTP source's client), or `null` when it has none. */
    fun run(loaded: LoadedExtension): String? {
        val scheme = schemes[loaded.extension.pkgName] ?: return null
        val source = loaded.sources.filterIsInstance<HttpSource>().firstOrNull() ?: error("no HTTP source to fetch a page with")
        return check(source.client, scheme)
    }

    /** Serves a page scrambled by [scheme], fetches it through [client] and compares what comes back with the page. */
    fun check(client: OkHttpClient, scheme: Scheme): String {
        val page = page(WIDTH, HEIGHT)
        val scrambled = scheme.scramble(page, WIDTH, HEIGHT)
        val scrambledBy = meanDifference(scrambled, page)
        check(scrambledBy > SCRAMBLED_AT_LEAST) { "the ${scheme.name} test page is not scrambled ($scrambledBy)" }
        val png = png(scrambled, WIDTH, HEIGHT)
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/page.png") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, png.size.toLong())
            exchange.responseBody.use { it.write(png) }
        }
        server.start()
        try {
            val url = "http://${server.address.address.hostAddress}:${server.address.port}/page.png#${scheme.fragment}"
            val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code} for the test page" }
                response.body.bytes()
            }
            val shown = ImageIO.read(ByteArrayInputStream(bytes)) ?: error("the page came back as no image (${bytes.size} bytes)")
            check(shown.width == WIDTH && shown.height == HEIGHT) { "the page came back ${shown.width}x${shown.height}" }
            val difference = meanDifference(shown.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH), page)
            check(difference < TOLERANCE) {
                "the ${scheme.name} page came back wrong: %.2f levels from the page on average (scrambled: %.1f)"
                    .format(difference, scrambledBy)
            }
            return "${scheme.name} page unscrambled: %.2f levels from the page on average (scrambled: %.1f), page %s, returned %s"
                .format(difference, scrambledBy, sha256(png).take(12), sha256(bytes).take(12))
        } finally {
            server.stop(0)
        }
    }

    /** Red follows x, green follows y, blue the diagonal: smooth, and no two places alike. */
    fun page(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        (0xff shl 24) or (x * 255 / (width - 1) shl 16) or (y * 255 / (height - 1) shl 8) or ((x + y) * 255 / (width + height - 2))
    }

    fun meanDifference(actual: IntArray, expected: IntArray): Double {
        var sum = 0L
        for (i in actual.indices) {
            for (shift in intArrayOf(0, 8, 16)) sum += abs((actual[i] shr shift and 0xff) - (expected[i] shr shift and 0xff))
        }
        return sum / (actual.size * 3.0)
    }

    private fun png(argb: IntArray, width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, width, height, argb, 0, width) }
        return ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
