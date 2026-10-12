package exh.md.handlers

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.MangaUpViewerResponse
import exh.md.utils.evaluateOnSite
import exh.md.utils.jsString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Adapted from https://github.com/keiyoushi/extensions-source/tree/main/src/all/mangaup
 */
class MangaUpHandler(currentClient: OkHttpClient) {
    private val domain = "manga-up.com"
    private val apiUrl = "https://global-api.$domain/api"
    private val imgUrl = "https://global-img.$domain"

    private var secret: String? = null

    // Set once the WebView was checked for a secret, even if none was found, so logged-out users don't
    // spin up a WebView for every chapter
    private var secretLoaded = false

    private val secretMutex = Mutex()

    val baseUrl = "https://global.$domain"

    val headers = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")
        .build()

    val client = currentClient.newBuilder()
        .addInterceptor(MangaUpImageInterceptor)
        .build()

    private suspend fun fetchSecret(): String? = secretMutex.withLock {
        if (!secretLoaded) {
            secret = evaluateOnSite(baseUrl, "localStorage.getItem('secret')")?.takeUnless { it.isBlank() }
            secretLoaded = true
        }
        secret
    }

    /**
     * Drops [failed] from the WebView's localStorage if it's still stored there, then reads the secret again.
     *
     * @return the new secret, or `null` to continue logged out.
     */
    private suspend fun refreshSecret(failed: String?): String? = secretMutex.withLock {
        // Another request already replaced the failed secret
        secret?.takeIf { it != failed }?.let { return it }

        val script = """
            (function() {
                if (localStorage.getItem('secret') === ${jsString(failed)}) localStorage.removeItem('secret');
                return localStorage.getItem('secret');
            })()
        """.trimIndent()
        secret = evaluateOnSite(baseUrl, script)?.takeUnless { it.isBlank() || it == failed }
        secretLoaded = true
        secret
    }

    /** Makes the next [fetchSecret] check the WebView again if no secret was found, e.g. after a login there */
    private suspend fun recheckMissingSecret() = secretMutex.withLock {
        if (secret == null) secretLoaded = false
    }

    private suspend fun viewerRequest(url: HttpUrl): Response {
        val response = client.newCall(POST(url.toString(), headers)).await()

        // 410: expired secret -> device got removed OR more than 3 secrets/devices active, oldest one expires
        // 401: invalid secret -> login was aborted; UA mismatch from previous login
        if (response.code != 410 && response.code != 401) {
            if (!response.isSuccessful) {
                response.close()
                throw HttpException(response.code)
            }
            return response
        }
        response.close()

        val newSecret = refreshSecret(url.queryParameter("secret"))
        val retryUrl = url.newBuilder()
            .apply {
                if (newSecret != null) setQueryParameter("secret", newSecret) else removeAllQueryParameters("secret")
            }
            .build()

        return client.newCall(POST(retryUrl.toString(), headers)).awaitSuccess()
    }

    suspend fun fetchPageList(externalUrl: String, lang: String): List<Page> {
        val chapterId = externalUrl.substringBefore("?").substringAfterLast("/").toIntOrNull()
            ?: throw Exception("Unsupported Manga UP! chapter link: $externalUrl")
        val url = "$apiUrl/manga/viewer_v2".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("chapter_id", chapterId.toString())
            .addQueryParameter("quality", "high")
            .addQueryParameter("lang", lang)
            .build()

        val result = viewerRequest(url).use { ProtoBuf.decodeFromByteArray<MangaUpViewerResponse>(it.body.bytes()) }
        // The response also carries blocks for other chapters, keep only the requested one
        val pages = result.pageBlocks
            .firstOrNull { it.chapterId == chapterId }
            ?.pages
            ?.filterNot { it.url.contains("tutorial") }

        if (pages.isNullOrEmpty()) {
            // Check the WebView again next time in case the user logs in meanwhile
            recheckMissingSecret()
            throw Exception("Log in via WebView and purchase this chapter")
        }

        return pages.mapIndexed { i, page ->
            val encryption = if (page.key != null && page.iv != null) "#key=${page.key}#iv=${page.iv}" else ""
            Page(i, imageUrl = imgUrl + page.url + encryption)
        }
    }
}

private object MangaUpImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val parts = fragment.split("#")
        val key = parts.getOrNull(0)?.removePrefix("key=")
        val iv = parts.getOrNull(1)?.removePrefix("iv=")

        if (key.isNullOrEmpty() || iv.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val cipher = try {
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key.decodeHex(), "AES"), IvParameterSpec(iv.decodeHex()))
            }
        } catch (e: Exception) {
            throw IOException("Invalid Manga UP! image key", e)
        }

        val response = chain.proceed(request)

        if (!response.isSuccessful) return response

        val body = response.body.source().cipherSource(cipher).buffer().asResponseBody(response.body.contentType())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun String.decodeHex(): ByteArray {
        require(length % 2 == 0) { "Unexpected hex string: $this" }

        val result = ByteArray(length / 2)
        for (i in result.indices) {
            val d1 = decodeHexDigit(this[i * 2]) shl 4
            val d2 = decodeHexDigit(this[i * 2 + 1])
            result[i] = (d1 + d2).toByte()
        }
        return result
    }

    private fun decodeHexDigit(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Unexpected hex digit: $c")
        }
    }
}
