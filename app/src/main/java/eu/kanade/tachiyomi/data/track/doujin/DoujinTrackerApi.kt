package eu.kanade.tachiyomi.data.track.doujin

import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class DoujinTrackerApi(
    private val client: OkHttpClient,
) {
    private val json: Json by injectLazy()

    suspend fun login(username: String, password: String): LoginResponse {
        val payload = """{"username":"$username","password":"$password"}"""
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(payload.toRequestBody(jsonMime))
            .header("Content-Type", "application/json")
            .build()

        return with(json) {
            client.newCall(request)
                .awaitSuccess()
                .parseAs<LoginResponse>()
        }
    }

    suspend fun refreshSession(refreshToken: String): LoginResponse {
        val payload = """{"refreshToken":"$refreshToken"}"""
        val request = Request.Builder()
            .url("$baseUrl/auth/refresh")
            .post(payload.toRequestBody(jsonMime))
            .header("Content-Type", "application/json")
            .build()

        return with(json) {
            client.newCall(request)
                .awaitSuccess()
                .parseAs<LoginResponse>()
        }
    }

    suspend fun getTitles(token: String): TitlesResponse {
        val request = Request.Builder()
            .url("$baseUrl/tracker/titles")
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        return with(json) {
            client.newCall(request)
                .awaitSuccess()
                .parseAs<TitlesResponse>()
        }
    }

    suspend fun addTitle(token: String, requestBody: AddTitleRequest): AddTitleResponse {
        val request = Request.Builder()
            .url("$baseUrl/tracker/add")
            .post(json.encodeToString(AddTitleRequest.serializer(), requestBody).toRequestBody(jsonMime))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        return with(json) {
            client.newCall(request)
                .awaitSuccess()
                .parseAs<AddTitleResponse>()
        }
    }

    suspend fun updateTitle(token: String, titleId: String, requestBody: UpdateTitleRequest) {
        val request = Request.Builder()
            .url("$baseUrl/tracker/$titleId")
            .patch(json.encodeToString(UpdateTitleRequest.serializer(), requestBody).toRequestBody(jsonMime))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).awaitSuccess().close()
    }

    suspend fun deleteTitle(token: String, titleId: String) {
        val request = Request.Builder()
            .url("$baseUrl/tracker/$titleId")
            .delete()
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).awaitSuccess().close()
    }

    companion object {
        // Emulator localhost mapping; swap to your production bridge URL when needed.
        const val baseUrl = "https://doujin-tracker-bridge.onrender.com"
    }
}

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class TitlesResponse(
    val titles: List<TrackedTitle> = emptyList(),
)

@Serializable
data class TrackedTitle(
    val id: String,
    val title: String,
    val author: String? = null,
    val circle: String? = null,
    val parody: String? = null,
    val event: String? = null,
    val description: String? = null,
    val pages: Int? = null,
    val status: String,
    val progress: Int = 0,
    val totalChapters: Int? = null,
    val coverUrl: String? = null,
    val sourceId: String? = null,
)

@Serializable
data class AddTitleRequest(
    val title: String,
    val author: String? = null,
    val circle: String? = null,
    val parody: String? = null,
    val event: String? = null,
    val description: String? = null,
    val pages: Int? = null,
    val coverUrl: String? = null,
    val status: String,
    val progress: Int,
    val totalChapters: Int? = null,
    val sourceId: String? = null,
)

@Serializable
data class AddTitleResponse(
    val id: String,
)

@Serializable
data class UpdateTitleRequest(
    val status: String,
    val progress: Int,
)
