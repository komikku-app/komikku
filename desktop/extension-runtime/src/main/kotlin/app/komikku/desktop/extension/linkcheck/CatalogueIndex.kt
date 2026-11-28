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

    private val ABSOLUTE_APK = Regex("""https?://[\x21-\x7e]+?\.apk""")
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
            ABSOLUTE_APK.findAll(text).map { it.value }.toList()
        }
        return urls.distinct()
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
