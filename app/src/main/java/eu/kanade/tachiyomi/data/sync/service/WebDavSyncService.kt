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

    private fun buildWebDavFileUrl(baseUrl: String, folder: String, fileName: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanFolder = folder.trim('/')
        return if (cleanFolder.isNotEmpty()) {
            "$cleanBase/$cleanFolder/$fileName"
        } else {
            "$cleanBase/$fileName"
        }
    }


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
        val url = syncPreferences.webDavUrl().get()
        val folder = syncPreferences.webDavFolder().get()
        val username = syncPreferences.webDavUsername().get()
        val password = syncPreferences.webDavPassword().get()

        val credentials = Credentials.basic(username, password)
        val lastETag = syncPreferences.lastSyncEtag().get()

        val headersBuilder = Headers.Builder().add("Authorization", credentials)
        if (lastETag.isNotEmpty()) {
            headersBuilder.add("If-None-Match", lastETag)
        }

        val requestUrl = buildWebDavFileUrl(url, folder, "backup.proto")
        val request = GET(requestUrl, headers = headersBuilder.build())

        val client = OkHttpClient()
        val response = client.newCall(request).await()

        return when (response.code) {
            HttpStatus.SC_NOT_MODIFIED -> {
                logcat(LogPriority.INFO) { "Remote file not modified" }
                Pair(null, lastETag)
            }
            HttpStatus.SC_NOT_FOUND -> {
                logcat(LogPriority.INFO) { "No remote file found" }
                Pair(null, "")
            }
            else -> {
                if (response.isSuccessful) {
                    val newETag = response.headers["ETag"] ?: ""
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

        val url = syncPreferences.webDavUrl().get()
        val folder = syncPreferences.webDavFolder().get()
        val username = syncPreferences.webDavUsername().get()
        val password = syncPreferences.webDavPassword().get()
        val credentials = Credentials.basic(username, password)

        val timeout = 30L
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }

        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())
        val headersBuilder = Headers.Builder().add("Authorization", credentials)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }

        val requestUrl = buildWebDavFileUrl(url, folder, "backup.proto")
        val request = PUT(requestUrl, headers = headersBuilder.build(), body = body)

        val response = client.newCall(request).await()

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"] ?: ""
            if (newETag.isNotEmpty()) {
                syncPreferences.lastSyncEtag().set(newETag)
            }
            logcat(LogPriority.INFO) { "WebDAV sync completed" }
        } else if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
            logcat(LogPriority.WARN) { "WebDAV sync conflict (412)" }
        } else {
            val bodyStr = response.body.string()
            throw WebDavException("Upload failed: $bodyStr")
        }
    }
}

