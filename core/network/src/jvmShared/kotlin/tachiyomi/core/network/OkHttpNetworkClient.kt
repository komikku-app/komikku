package tachiyomi.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp-backed [NetworkClient] shared by the Android and desktop (JVM) targets.
 */
actual fun httpClient(): NetworkClient = OkHttpNetworkClient()

class OkHttpNetworkClient(
    private val client: OkHttpClient = OkHttpClient(),
) : NetworkClient {

    override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.addHeader(name, value) }
        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            NetworkResponse(
                statusCode = it.code,
                isSuccessful = it.isSuccessful,
                body = it.body.string(),
                headers = it.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") },
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
