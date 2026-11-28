package app.komikku.desktop.extension.linkcheck

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

/**
 * The APK URLs of an extension store index, for the catalogue link check: Keiyoushi's `index.pb` (protobuf, usually
 * gzip-compressed, absolute APK URLs) or a Tachiyomi `index.min.json` (APK file names under `apk/` next to the index).
 */
object CatalogueIndex {

    private val ABSOLUTE_APK = Regex("""https?://\S+\.apk""")
    private val JSON_APK = Regex(""""apk"\s*:\s*"([^"]+\.apk)"""")

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /** The distinct APK URLs listed by the index at [indexUrl], in index order. */
    fun apkUrls(indexUrl: String): List<String> = apkUrls(indexUrl, get(indexUrl))

    /** The distinct APK URLs listed by [index] (the bytes of the index at [indexUrl]). */
    fun apkUrls(indexUrl: String, index: ByteArray): List<String> {
        val bytes = if (index.size > 2 && index[0] == 0x1f.toByte() && index[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(index)).use { it.readBytes() }
        } else {
            index
        }
        val text = String(bytes, Charsets.ISO_8859_1)
        val json = JSON_APK.findAll(text).map { it.groupValues[1] }.toList()
        val urls = if (json.isNotEmpty()) {
            val base = indexUrl.substringBeforeLast('/') + "/apk/"
            json.map { name -> if ("://" in name) name else base + name }
        } else {
            mutableListOf<String>().also { protobufStrings(bytes, 0, bytes.size, it) }.filter(ABSOLUTE_APK::matches)
        }
        return urls.distinct()
    }

    /**
     * Adds the length-delimited fields of the protobuf message in `bytes[from, to)` to [out] as strings, recursively (a
     * payload that parses as a message is also searched). False when the range is not a message; [out] is then left unchanged.
     * Reading the fields, not searching the bytes, keeps a URL whole when its name contains `.apk` (`apkomik`).
     */
    private fun protobufStrings(bytes: ByteArray, from: Int, to: Int, out: MutableList<String>): Boolean {
        val found = mutableListOf<String>()
        var position = from
        fun varint(): Long? {
            var result = 0L
            var shift = 0
            while (position < to && shift < 64) {
                val byte = bytes[position++].toInt()
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            return null
        }
        while (position < to) {
            val key = varint() ?: return false
            if (key ushr 3 == 0L) return false
            when ((key and 7).toInt()) {
                0 -> varint() ?: return false
                1 -> position += 8
                5 -> position += 4
                2 -> {
                    val length = varint() ?: return false
                    if (length < 0 || length > to - position) return false
                    val end = position + length.toInt()
                    // A text can parse as a message by chance: keep it as a string either way
                    found += String(bytes, position, end - position, Charsets.UTF_8)
                    protobufStrings(bytes, position, end, found)
                    position = end
                }
                else -> return false
            }
            if (position > to) return false
        }
        out += found
        return true
    }

    /** Downloads [url] to [target] (three attempts). */
    fun download(url: String, target: File) {
        var failure: IOException? = null
        repeat(ATTEMPTS) {
            try {
                val response = client.send(request(url), HttpResponse.BodyHandlers.ofFile(target.toPath()))
                if (response.statusCode() == 200) return
                failure = IOException("HTTP ${response.statusCode()} for $url")
            } catch (e: IOException) {
                failure = e
            }
            target.delete()
        }
        throw failure!!
    }

    private fun get(url: String): ByteArray {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return (if (url.startsWith("file:")) File(URI(url)) else File(url)).readBytes()
        }
        val response = client.send(request(url), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()} for $url")
        return response.body()
    }

    private fun request(url: String): HttpRequest = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(2)).build()

    private const val ATTEMPTS = 3
}
