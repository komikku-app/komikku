package exh.md.handlers

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.BirthdayCookie
import exh.md.dto.LocalStorageAccount
import exh.md.dto.ViewerApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.getValue

/**
 * Adapted from https://github.com/keiyoushi/extensions-source/tree/main/src/en/kmanga
 */
class KMangaHandler(currentClient: OkHttpClient) {
    private val json by injectLazy<Json>()

    private var userId: Int? = null

    private var reloadUserId = false

    private val app = Injekt.get<Application>()

    private val executor = ContextCompat.getMainExecutor(app)

    val baseUrl = "https://kmanga.kodansha.com"

    val headers = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")
        .add("X-Kmanga-Platform", "3")
        .build()

    val client = currentClient
        .newBuilder()
        .addInterceptor(KMangaImageInterceptor)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 400) {
                response.close()
                val error = if (request.url.pathSegments.last().contains("viewer")) {
                    "Log in via WebView and rent or purchase this chapter to read."
                } else {
                    reloadUserId = true
                    "Open WebView and retry"
                }

                throw IOException(error)
            }
            response
        }
        .build()

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val episodeId = externalUrl.toHttpUrl().pathSegments.last()
        val url = "$API_URL/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episode_id", episodeId)
            .build()

        val result = with(json) { hashedReq(url).parseAs<ViewerApiResponse>() }
        return result.pageList.mapIndexed { index, page ->
            Page(index, imageUrl = "$page#${result.scrambleSeed}:${result.titleId}:${result.episodeId}")
        }
    }

    private suspend fun hashedReq(url: HttpUrl, body: FormBody? = null): Response {
        if (reloadUserId || userId == null) setUserId()

        val (birthday, expires) = getBirthdayCookie(url)
        val params = if (body != null) {
            (0 until body.size).associate { body.name(it) to body.value(it) }
        } else {
            url.queryParameterNames.associateWith {
                url.queryParameter(it)!!
            }
        }

        val hash = generateHash(params, birthday, expires)
        val newHeaders = headers.newBuilder()
            .add("x-kmanga-client-id", "0")
            .add("x-kmanga-is-crawler", "false")
            .add("X-Kmanga-Hash", hash)
            .build()

        return if (body != null) {
            client.newCall(POST(url.toString(), newHeaders, body)).awaitSuccess()
        } else {
            client.newCall(GET(url, newHeaders)).awaitSuccess()
        }
    }

    private fun getBirthdayCookie(url: HttpUrl): Pair<String, String> {
        val cookies = client.cookieJar.loadForRequest(url)
        val birthdayCookie = cookies.firstOrNull { it.name == "birthday" }?.value

        return if (birthdayCookie != null) {
            try {
                val decoded = URLDecoder.decode(birthdayCookie, "UTF-8")
                val cookieData = json.decodeFromString<BirthdayCookie>(decoded)
                cookieData.value to cookieData.expires.toString()
            } catch (_: Exception) {
                // Fallback to default if cookie is malformed
                "2000-01" to (System.currentTimeMillis() / 1000 + 315360000).toString()
            }
        } else {
            // Default for logged-out users or users without the cookie to bypass age restrictions
            "2000-01" to (System.currentTimeMillis() / 1000 + 315360000).toString()
        }
    }

    // https://kmanga.kodansha.com/_nuxt/vl9so/entry-CSwIbMdW.js
    private fun generateHash(params: Map<String, String>, birthday: String, expires: String): String {
        val paramStrings = params.toSortedMap().map { (key, value) ->
            getHashedParam(key, value)
        }

        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.encodeUtf8().sha256().hex()
        val cookieHash = getHashedParam(birthday, expires)
        val finalString = "$hash1$cookieHash"
        return finalString.encodeUtf8().sha512().hex()
    }

    private fun getHashedParam(key: String, value: String): String {
        val keyHash = key.encodeUtf8().sha256().hex()
        val valueHash = value.encodeUtf8().sha512().hex()
        return "${keyHash}_$valueHash"
    }

    private suspend fun setUserId() {
        reloadUserId = false
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var accountStr: String? = null

        executor.execute {
            val view = WebView(app)
            webView = view

            @SuppressLint("SetJavaScriptEnabled")
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = false
                useWideViewPort = false
                loadWithOverviewMode = false
            }

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("localStorage.getItem('account')") {
                        accountStr = json.decodeFromString<String?>(it)
                        latch.countDown()
                    }
                }
            }

            view.loadDataWithBaseURL(baseUrl, "", "text/html", "UTF-8", null)
        }

        withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) }

        executor.execute { webView?.destroy() }

        val account: LocalStorageAccount? = accountStr?.let(json::decodeFromString)

        userId = if (account?.isLoggedIn == true) {
            account.checkedTicketExpiredList?.firstOrNull()?.userId ?: 0
        } else {
            0
        }
    }

    companion object {
        private const val API_URL = "https://api.kmanga.kodansha.com"
    }
}

private object KMangaImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains(":")) {
            return response
        }

        val (seed, titleId, episodeId) = fragment.split(":")
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, seed, titleId.toInt(), episodeId.toInt())

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody(MEDIA_TYPE, buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private class Coord(val x: Int, val y: Int)
    private class CoordPair(val source: Coord, val dest: Coord)

    private fun UInt.xorshift32(): UInt {
        var n = this
        n = n xor (n shl 13)
        n = n xor (n shr 17)
        n = n xor (n shl 5)
        return n
    }

    private fun getUnscrambledCoords(seed: String, titleId: Int, episodeId: Int): List<CoordPair> {
        // WASM decrypts two 10-byte arrays to use as substitution charsets.
        // Selects the charset based on titleId % 2
        val charset = if (titleId % 2 == 0) CHARSET_EVEN else CHARSET_ODD

        var parsedInt = 0UL

        // Maps the string into a base-10 number using the selected charset
        for (char in seed) {
            val index = charset.indexOf(char)
            if (index != -1) {
                parsedInt = parsedInt * 10UL + index.toULong()
            } else {
                break
            }
        }

        // The final 32-bit seed is xor'd against the sum of titleId and episodeId
        var seed32 = parsedInt.toUInt() xor titleId.toUInt() + episodeId.toUInt()

        val pairs = mutableListOf<Pair<UInt, Int>>()

        for (i in 0 until 16) {
            seed32 = seed32.xorshift32()
            pairs.add(seed32 to i)
        }

        pairs.sortBy { it.first }

        return pairs.mapIndexed { destIndex, (_, sourceIndex) ->
            CoordPair(
                source = Coord(x = sourceIndex % GRID_SIZE, y = sourceIndex / GRID_SIZE),
                dest = Coord(x = destIndex % GRID_SIZE, y = destIndex / GRID_SIZE),
            )
        }
    }

    private fun unscramble(image: Bitmap, seed: String, titleId: Int, episodeId: Int): Bitmap {
        val unscrambledCoords = getUnscrambledCoords(seed, titleId, episodeId)
        val width = image.width
        val height = image.height
        val result = createBitmap(width, height)
        val canvas = Canvas(result)

        val blockWidth = (width and -8) / 4
        val blockHeight = (height and -8) / 4
        val srcRect = Rect()
        val dstRect = Rect()

        unscrambledCoords.forEach {
            val srcX = it.source.x * blockWidth
            val srcY = it.source.y * blockHeight
            val dstX = it.dest.x * blockWidth
            val dstY = it.dest.y * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        val processedWidth = blockWidth * GRID_SIZE
        val processedHeight = blockHeight * GRID_SIZE

        if (width > processedWidth) {
            srcRect.set(processedWidth, 0, width, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }
        if (height > processedHeight) {
            srcRect.set(0, processedHeight, processedWidth, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }

        return result
    }

    private val MEDIA_TYPE = "image/jpeg".toMediaType()
    private const val GRID_SIZE = 4
    private const val CHARSET_EVEN = "we7ru3ty8i"
    private const val CHARSET_ODD = "h4xm9bqz1p"
}
