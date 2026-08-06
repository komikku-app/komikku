package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class TelegramSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private val client = Injekt.get<NetworkHelper>().client

    override suspend fun doSync(syncData: SyncData): Backup? {
        val token = syncPreferences.telegramToken().get()
        val chatId = syncPreferences.telegramChatId().get()

        if (token.isBlank() || chatId.isBlank()) {
            notifier.showSyncError("Telegram token or chat ID is missing")
            return null
        }

        try {
            val remoteSyncData = pullSyncData(token, chatId)

            val finalSyncData = if (remoteSyncData != null) {
                logcat(LogPriority.DEBUG) { "Merging local and remote sync data from Telegram" }
                mergeSyncData(syncData, remoteSyncData)
            } else {
                logcat(LogPriority.DEBUG) { "No remote sync data found on Telegram, using local" }
                syncData
            }

            pushSyncData(token, chatId, finalSyncData)
            return finalSyncData.backup
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e) { "Telegram sync error" }
            notifier.showSyncError(e.message ?: "Unknown Telegram error")
            return null
        }
    }

    private suspend fun pullSyncData(token: String, chatId: String): SyncData? {
        val chat = getChat(token, chatId)
        val pinnedMessage = chat?.pinned_message ?: return null
        val document = pinnedMessage.document ?: return null

        logcat(LogPriority.DEBUG) { "Found pinned backup message: ${pinnedMessage.message_id}" }
        return downloadBackup(token, document.file_id, pinnedMessage.caption)
    }

    private suspend fun pushSyncData(token: String, chatId: String, syncData: SyncData) {
        val backup = syncData.backup ?: return
        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)

        val chat = getChat(token, chatId)
        val previousMessageId = chat?.pinned_message?.message_id

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart(
                "document",
                "comick_sync.proto.gz",
                byteArray.toRequestBody("application/octet-stream".toMediaType()),
            )
            .addFormDataPart(
                "caption",
                "Comick Sync Backup | Device: ${android.os.Build.MODEL} | ID: ${syncData.deviceId}" +
                    (if (previousMessageId != null) " | Prev: $previousMessageId" else ""),
            )
            .build()

        val request = POST("https://api.telegram.org/bot$token/sendDocument", body = body)
        val response = client.newCall(request).await()

        if (!response.isSuccessful) {
            throw IOException("Failed to upload backup to Telegram: ${response.body.string()}")
        }

        val responseBody = response.body.string()
        val result = try {
            json.decodeFromString<TelegramResponse<TelegramMessage>>(responseBody)
        } catch (e: Exception) {
            throw IOException("Failed to parse Telegram response: $responseBody")
        }

        val messageId = result.result?.message_id ?: throw IOException("Failed to get message_id from Telegram: ${result.description}")

        pinMessage(token, chatId, messageId)
    }

    private suspend fun getChat(token: String, chatId: String): TelegramChat? {
        val request = GET("https://api.telegram.org/bot$token/getChat?chat_id=$chatId")
        val response = client.newCall(request).await()
        if (!response.isSuccessful) return null

        val responseBody = response.body.string()
        val result = try {
            json.decodeFromString<TelegramResponse<TelegramChat>>(responseBody)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to parse getChat response" }
            return null
        }
        return result.result
    }

    private suspend fun pinMessage(token: String, chatId: String, messageId: Long) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("message_id", messageId.toString())
            .build()

        val request = POST("https://api.telegram.org/bot$token/pinChatMessage", body = body)
        client.newCall(request).await().close()
    }

    private suspend fun downloadBackup(token: String, fileId: String, caption: String?): SyncData? {
        val fileRequest = GET("https://api.telegram.org/bot$token/getFile?file_id=$fileId")
        val fileResponse = client.newCall(fileRequest).await()
        if (!fileResponse.isSuccessful) return null

        val fileResponseBody = fileResponse.body.string()
        val fileResult = try {
            json.decodeFromString<TelegramResponse<TelegramFile>>(fileResponseBody)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to parse getFile response" }
            return null
        }
        val filePath = fileResult.result?.file_path ?: return null

        val downloadRequest = GET("https://api.telegram.org/file/bot$token/$filePath")
        val downloadResponse = client.newCall(downloadRequest).await()
        if (!downloadResponse.isSuccessful) return null

        val byteArray = downloadResponse.body.bytes()
        return try {
            val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
            val deviceId = caption?.substringAfter("ID: ")?.substringBefore(" ") ?: ""
            SyncData(deviceId = deviceId, backup = backup)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to decode backup from Telegram" }
            null
        }
    }

    @Serializable
    private data class TelegramResponse<T>(
        val ok: Boolean,
        val result: T? = null,
        val description: String? = null,
    )

    @Serializable
    private data class TelegramChat(
        val id: Long,
        val pinned_message: TelegramMessage? = null,
    )

    @Serializable
    private data class TelegramMessage(
        val message_id: Long,
        val document: TelegramDocument? = null,
        val caption: String? = null,
    )

    @Serializable
    private data class TelegramDocument(
        @SerialName("file_id") val file_id: String,
    )

    @Serializable
    private data class TelegramFile(
        @SerialName("file_id") val file_id: String,
        @SerialName("file_path") val file_path: String? = null,
    )
}
