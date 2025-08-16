package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class WebDavSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private class WebDavException(message: String?) : Exception(message)

    private val url: String = syncPreferences.webDavUrl().get().trim()
    private val folder: String = syncPreferences.webDavFolder().get().trim('/')
    private val username: String = syncPreferences.webDavUsername().get().trim()
    private val password: String = syncPreferences.webDavPassword().get().trim()
    private val credentials: String = Credentials.basic(username, password)

    private fun buildWebDavFileUrl(fileName: String): String {
        val cleanBase = url.trimEnd('/')
        return if (folder.isNotEmpty()) "$cleanBase/$folder/$fileName" else "$cleanBase/$fileName"
    }

    private fun buildCustomOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doSync(syncData: SyncData): Backup? {
        try {
            val (remoteData, etag) = pullSyncData()

            val finalSyncData = if (remoteData != null) {
                assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logcat(LogPriority.DEBUG) { "Merging local and remote data with ETag($etag)" }
                mergeSyncData(syncData, remoteData)
            } else {
                logcat(LogPriority.DEBUG) { "No remote data, using local syncData" }
                syncData
            }

            pushSyncData(finalSyncData, etag)
            return finalSyncData.backup
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "WebDAV sync error: ${e.message}" }
            notifier.showSyncError(e.message)
            return null
        }
    }

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        if (url.isEmpty() || !url.startsWith("http")) {
            notifier.showSyncError("Invalid WebDAV URL. Please check your settings.")
            return Pair(null, "")
        }

        if (username.isBlank() || password.isBlank()) {
            notifier.showSyncError("Username or password cannot be empty.")
            return Pair(null, "")
        }

        val lastETag = syncPreferences.lastSyncEtag().get()
        val headersBuilder = Headers.Builder().add("Authorization", credentials)
        if (lastETag.isNotEmpty()) headersBuilder.add("If-None-Match", lastETag)

        val requestUrl = buildWebDavFileUrl("backup.proto")
        val request = GET(requestUrl, headers = headersBuilder.build())
        val client = buildCustomOkHttpClient()
        val response = client.newCall(request).await()

        return when (response.code) {
            HttpStatus.SC_NOT_MODIFIED -> Pair(null, lastETag)
            HttpStatus.SC_NOT_FOUND -> Pair(null, "")
            else -> {
                if (response.isSuccessful) {
                    val newETag = response.headers["ETag"]?.trim('"') ?: ""
                    val bytes = response.body.byteStream().use { it.readBytes() }
                    try {
                        val backup = protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
                        Pair(SyncData(backup = backup), newETag)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Invalid backup format: ${e.message}" }
                        Pair(null, "")
                    }
                } else {
                    val body = response.body.string()
                    throw WebDavException("Failed to download: $body")
                }
            }
        }
    }

    private suspend fun pushSyncData(syncData: SyncData, eTag: String) {
        val backup = syncData.backup ?: return

        if (url.isEmpty() || !url.startsWith("http")) {
            notifier.showSyncError("Invalid WebDAV URL. Please check your settings.")
            return
        }

        if (username.isBlank() || password.isBlank()) {
            notifier.showSyncError("Username or password cannot be empty.")
            return
        }

        val client = buildCustomOkHttpClient()
        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))

        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())
        val headersBuilder = Headers.Builder().add("Authorization", credentials)
        if (eTag.isNotEmpty()) headersBuilder.add("If-Match", eTag)

        val requestUrl = buildWebDavFileUrl("backup.proto")
        val request = PUT(requestUrl, headers = headersBuilder.build(), body = body)
        val response = client.newCall(request).await()

        when {
            response.isSuccessful -> {
                val newETag = response.headers["ETag"]?.trim('"') ?: ""
                if (newETag.isNotEmpty()) syncPreferences.lastSyncEtag().set(newETag)
                logcat(LogPriority.INFO) { "WebDAV sync completed" }
            }
            response.code == HttpStatus.SC_PRECONDITION_FAILED -> {
                val message = "Sync conflict detected. Another device may have updated the remote backup. " +
                    "Please retry syncing or check your WebDAV folder."
                logcat(LogPriority.WARN) { message }
                notifier.showSyncError(message)
            }
            else -> {
                val bodyStr = response.body.string()
                throw WebDavException("Upload failed: $bodyStr")
            }
        }
    }
}
