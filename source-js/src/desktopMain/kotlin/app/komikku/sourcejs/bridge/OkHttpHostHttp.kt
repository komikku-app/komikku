package app.komikku.sourcejs.bridge

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.http.HttpMethod
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HostHttp] over an [OkHttpClient] supplied by the app (`NetworkHelper.client`, with its cookie jar, rate
 * limits, DNS-over-HTTPS and Cloudflare interceptors). Certificates are verified by OkHttp as usual.
 *
 * The call is asynchronous (it does not block the JavaScript thread) and is cancelled with the coroutine.
 */
class OkHttpHostHttp(private val client: OkHttpClient) : HostHttp {

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        val callClient = if (request.followRedirects) client else client.newBuilder().followRedirects(false).build()
        val call = callClient.newCall(request.toOkHttpRequest())
        request.timeoutMillis?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use {
                                HostHttpResponse(
                                    statusCode = it.code,
                                    headers = it.headers.map { header -> header.first to header.second },
                                    body = it.readBody(request.maxBodyBytes),
                                    finalUrl = it.request.url.toString(),
                                    reasonPhrase = it.message.ifEmpty { null },
                                )
                            }
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                            return
                        }
                        continuation.resume(result)
                    }
                },
            )
        }
    }

    /** The body, failing as soon as it is known to exceed [maxBytes] (declared length or bytes read). */
    private fun Response.readBody(maxBytes: Long?): ByteArray {
        if (maxBytes == null) return body.bytes()
        val declared = body.contentLength()
        if (declared > maxBytes) throw IOException("response body larger than $maxBytes bytes")
        val source = body.source()
        if (source.request(maxBytes + 1)) throw IOException("response body larger than $maxBytes bytes")
        return source.readByteArray()
    }

    private fun HostHttpRequest.toOkHttpRequest(): Request {
        val headers = Headers.Builder().apply { this@toOkHttpRequest.headers.forEach { (name, value) -> add(name, value) } }.build()
        val requestBody = when {
            body != null -> body.toRequestBody()
            HttpMethod.requiresRequestBody(method) -> ByteArray(0).toRequestBody()
            else -> null
        }
        return Request.Builder()
            .url(url)
            .headers(headers)
            .method(method, requestBody)
            .build()
    }
}
