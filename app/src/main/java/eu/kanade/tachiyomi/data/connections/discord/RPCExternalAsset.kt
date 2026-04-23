package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RPCExternalAsset(
    applicationId: String,
    private val token: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    companion object {
        private const val TAG = "RPCExternalAsset"
    }

    @Serializable
    data class ExternalAsset(
        val url: String? = null,
        @SerialName("external_asset_path")
        val externalAssetPath: String? = null,
    )

    private val api = "https://discord.com/api/v9/applications/$applicationId/external-assets"
    suspend fun getDiscordUri(imageUrl: String): String? {
        if (imageUrl.startsWith("mp:")) return imageUrl
        val request = Request.Builder().url(api).header("Authorization", token)
            .post("{\"urls\":[\"$imageUrl\"]}".toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).await().use { res ->
                if (res.code == 429) {
                    // Rate limit hit
                    Timber.tag(TAG).e("Discord API rate limit reached: ${res.body.string()}")
                    return null
                }
                if (!res.isSuccessful) {
                    Timber.tag(TAG).e("Discord API error: HTTP ${res.code} - ${res.body.string()}")
                    return null
                }
                json.decodeFromString<List<ExternalAsset>>(res.body.string())
                    .firstOrNull()?.externalAssetPath?.let { "mp:$it" }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Exception while fetching Discord external asset: ${e.message}")
            null
        }
    }

    private suspend inline fun Call.await(): Response {
        return suspendCoroutine {
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    it.resume(response)
                }
            })
        }
    }
}
