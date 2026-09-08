package exh.md.handlers

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.MangaUpViewerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    private val context = Injekt.get<Application>()

    private val executor = ContextCompat.getMainExecutor(context)

    private var secret: String? = null

    private val secretMutex = Mutex()

    val baseUrl = "https://global.$domain"

    val headers = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")
        .build()

    val client = currentClient.newBuilder()
        .addInterceptor(MangaUpImageInterceptor)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            // 410: expired secret -> device got removed OR more than 3 secrets/devices active, oldest one expires
            // 401: invalid secret -> login was aborted; UA mismatch from previous login
            if (response.code == 410 || response.code == 401) {
                response.close()

                val failedSecret = request.url.queryParameter("secret")

                if (failedSecret != null) {
                    flushSecret(failedSecret)

                    runBlocking {
                        secretMutex.withLock {
                            if (secret == failedSecret) {
                                secret = null
                            }
                        }
                    }
                }

                val newSecret = runBlocking { fetchSecret() }
                val isMyPage = request.url.pathSegments.lastOrNull() == "my_page"
                val isSecretValid = !newSecret.isNullOrEmpty() && newSecret != failedSecret

                if (!isSecretValid && isMyPage) {
                    val target = request.url.fragment
                    throw IOException("Log in via WebView to access your $target")
                }

                val newUrl = request.url.newBuilder()

                if (isSecretValid) {
                    newUrl.setQueryParameter("secret", newSecret)
                } else {
                    newUrl.removeAllQueryParameters("secret")

                    runBlocking {
                        secretMutex.withLock {
                            if (secret == newSecret) {
                                secret = null
                            }
                        }
                    }
                }

                val newRequest = request.newBuilder()
                    .url(newUrl.build())
                    .build()

                return@addInterceptor chain.proceed(newRequest)
            }
            response
        }
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchSecret(): String? = secretMutex.withLock {
        if (secret != null) return secret

        val latch = CountDownLatch(1)
        var token: String? = null

        executor.execute {
            val webView = WebView(context)

            @Suppress("DEPRECATION")
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("window.localStorage.getItem('secret')") { value ->
                        token = value?.trim('"')
                        if (token == "null" || token.isNullOrBlank()) token = null

                        latch.countDown()
                        view.stopLoading()
                        view.destroy()
                    }
                }
            }

            webView.loadDataWithBaseURL("$baseUrl/", " ", "text/html", "utf-8", null)
        }

        withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) }

        secret = token
        return secret
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun flushSecret(target: String) = executor.execute {
        val webView = WebView(context)

        @Suppress("DEPRECATION")
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            blockNetworkImage = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val script = "if(window.localStorage.getItem('secret')==='$target'){window.localStorage.removeItem('secret');}"
                view.evaluateJavascript(script) {
                    view.stopLoading()
                    view.destroy()
                }
            }
        }

        webView.loadDataWithBaseURL("$baseUrl/", " ", "text/html", "utf-8", null)
    }

    suspend fun fetchPageList(externalUrl: String, lang: String): List<Page> {
        val chapterId = externalUrl.substringAfterLast("/")
        val url = "$apiUrl/manga/viewer_v2".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("quality", "high")
            .addQueryParameter("lang", lang)
            .build()
            .toString()

        val response = client.newCall(POST(url, headers)).awaitSuccess()
        val result = response.use { ProtoBuf.decodeFromByteArray<MangaUpViewerResponse>(it.body.bytes()) }
        val pages = result.pageBlocks.flatMap { it.pages }
            .filter { !it.url.contains("tutorial") }

        if (pages.isEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter")
        }

        return pages.mapIndexed { i, page ->
            val img = imgUrl + page.url + "#key=${page.key}#iv=${page.iv}"
            Page(i, imageUrl = img)
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

        val response = chain.proceed(request)

        if (!response.isSuccessful) return response

        val secretKey = SecretKeySpec(key.decodeHex(), "AES")
        val ivSpec = IvParameterSpec(iv.decodeHex())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
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
