package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import android.os.Build
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    // KMK -->
    companion object {
        private val client by lazy { OkHttpClient() }
    }
    // KMK <--

    private class SyncYomiException(message: String?) : Exception(message)

    // KMK -->
    private class GzippedBackupRequestBody(
        private val backup: Backup,
        private val protoBuf: ProtoBuf,
    ) : RequestBody() {
        override fun contentType(): MediaType = "application/octet-stream".toMediaType()

        override fun contentLength(): Long = -1L

        override fun writeTo(sink: BufferedSink) {
            GzipSink(sink).buffer().use { compressed ->
                val writer = eu.kanade.tachiyomi.data.backup.BackupWriter(compressed, protoBuf)
                backup.backupManga.forEach(writer::writeEntry)
                writer.writeMetadata(backup.copy(backupManga = emptyList()))
            }
        }
    }
    // KMK <--

    @Serializable
    private data class SyncEvent(
        val event: SyncEventStatus,
        @SerialName("device_name")
        val deviceName: String? = null,
        val message: String? = null,
    )

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    // KMK -->
    override suspend fun doSync(syncData: SyncData): SyncResult {
        reportSyncEvent(SyncEventStatus.SYNC_STARTED)

        return try {
            if (supportsV2()) {
                doSyncV2(syncData)
            } else {
                doSyncV1(syncData)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, e.message)
            SyncResult(null, changed = false, protocolV2 = false)
        }
    }
    // KMK <--

    // KMK -->
    private suspend fun doSyncV1(syncData: SyncData): SyncResult {
        val (remoteData, etag) = pullSyncData()
        val finalSyncData = if (remoteData != null) {
            mergeSyncData(syncData, remoteData)
        } else {
            syncData
        }
        val success = pushSyncData(finalSyncData, etag)
        if (success) {
            reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        } else {
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Failed to push sync data")
            return SyncResult(null, changed = false, protocolV2 = false)
        }
        return SyncResult(finalSyncData.backup, changed = remoteData != null, protocolV2 = false)
    }

    private suspend fun doSyncV2(syncData: SyncData): SyncResult {
        val backup = syncData.backup ?: return SyncResult(null, changed = false, protocolV2 = true)
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val full = needsFullSync()
        val upload = if (full) {
            backup
        } else {
            backup.copy(backupManga = changedSince(backup.backupManga, syncPreferences.lastPushedAt().get()))
        }
        val headers = Headers.Builder()
            .add("X-API-Token", apiKey)
            .add("X-Device-ID", syncPreferences.uniqueDeviceID())
            .add("X-Device-Name", Build.MODEL)
            .add("X-Sync-Cursor", syncPreferences.syncCursor().get().toString())
            .add("X-Sync-Full", full.toString())
            .add("Content-Encoding", "gzip")
            .build()
        val body = GzippedBackupRequestBody(upload, protoBuf)
        val request = POST("$host/api/sync/v2/merge", headers = headers, body = body)
        val response = client.newCall(request).await()
        response.use {
            if (!it.isSuccessful) {
                val error = it.body.string()
                throw SyncYomiException("Failed to sync: ${it.code} $error")
            }
            val remote = protoBuf.decodeFromByteArray(Backup.serializer(), it.body.bytes())
            val cursor = it.headers["X-Sync-Cursor"]?.toLongOrNull()
                ?: throw SyncYomiException("Missing X-Sync-Cursor")
            val changed = it.headers["X-Sync-Changed"]?.toBoolean() ?: true
            val fullRequested = it.headers["X-Sync-Full-Requested"]?.toBoolean() ?: false
            syncPreferences.syncCursor().set(cursor)
            syncPreferences.fullSyncRequested().set(fullRequested)
            syncPreferences.lastPushedAt().set(syncData.syncStartedAt)
            if (full) syncPreferences.lastFullSync().set(System.currentTimeMillis())
            logcat(LogPriority.DEBUG) {
                "SyncYomi v2 merge completed: cursor=$cursor changed=$changed full=$full"
            }
            reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
            return SyncResult(remote, changed = changed, protocolV2 = true)
        }
    }

    private suspend fun supportsV2(): Boolean {
        val host = syncPreferences.clientHost().get()
        val probedHost = syncPreferences.v2ProbedHost().get()
        if (probedHost == host) {
            return syncPreferences.serverSupportsV2().get()
        }
        val request = GET(
            "$host/api/sync/v2/capabilities",
            headers = Headers.Builder()
                .add("X-API-Token", syncPreferences.clientAPIKey().get())
                .add("X-Device-ID", syncPreferences.uniqueDeviceID())
                .add("X-Device-Name", Build.MODEL)
                .build(),
        )
        val response = client.newCall(request).await()
        val supported = response.use {
            when (it.code) {
                HttpStatus.SC_OK -> true
                HttpStatus.SC_NOT_FOUND -> false
                else -> throw SyncYomiException("Failed to probe SyncYomi capabilities: ${it.code}")
            }
        }
        // KMK --> Cursor and push timestamps belong to the configured server.
        // Start with a full sync when the server changes.
        syncPreferences.syncCursor().set(0L)
        syncPreferences.lastPushedAt().set(0L)
        syncPreferences.lastFullSync().set(0L)
        syncPreferences.fullSyncRequested().set(false)
        // KMK <--
        syncPreferences.v2ProbedHost().set(host)
        syncPreferences.serverSupportsV2().set(supported)
        return supported
    }

    private fun needsFullSync(): Boolean {
        val now = System.currentTimeMillis()
        val lastFull = syncPreferences.lastFullSync().get()
        return syncPreferences.syncCursor().get() == 0L ||
            syncPreferences.fullSyncRequested().get() ||
            lastFull == 0L ||
            now - lastFull >= TimeUnit.DAYS.toMillis(1)
    }

    private fun changedSince(manga: List<BackupManga>, since: Long): List<BackupManga> =
        manga.filter { it.lastModifiedAt >= since || it.chapters.any { chapter -> chapter.lastModifiedAt >= since } }
            .onEach { it.chapters = it.chapters.filter { chapter -> chapter.lastModifiedAt >= since } }
    // KMK <--

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        val lastETag = syncPreferences.lastSyncEtag().get()
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest = GET(
            url = downloadUrl,
            headers = headers,
        )

        val response = client.newCall(downloadRequest).await()

        if (response.code == HttpStatus.SC_NOT_MODIFIED) {
            // not modified
            assert(lastETag.isNotEmpty())
            logcat(LogPriority.INFO) {
                "Remote server not modified"
            }
            return Pair(null, lastETag)
        } else if (response.code == HttpStatus.SC_NOT_FOUND) {
            // maybe got deleted from remote
            return Pair(null, "")
        }

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")

            val byteArray = response.body.byteStream().use {
                return@use it.readBytes()
            }

            return try {
                val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (_: SerializationException) {
                logcat(LogPriority.INFO) {
                    "Bad content responsed from server"
                }
                // the body is invalid
                // return default value so we can overwrite it
                Pair(null, "")
            }
        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to download sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    /**
     * Return true if update success
     */
    private suspend fun pushSyncData(syncData: SyncData, eTag: String): Boolean {
        val backup = syncData.backup ?: return true

        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }
        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())

        val uploadRequest = PUT(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        client.newCall(uploadRequest).await()
            // KMK -->
            .use { response ->
                // KMK <--
                if (response.isSuccessful) {
                    val newETag = response.headers["ETag"]
                        .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
                    syncPreferences.lastSyncEtag().set(newETag)
                    logcat(LogPriority.DEBUG) { "SyncYomi sync completed" }
                    return true
                } else if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
                    // other clients updated remote data, will try next time
                    logcat(LogPriority.DEBUG) { "SyncYomi sync failed with 412" }
                    return false
                } else {
                    val responseBody = response.body.string()
                    notifier.showSyncError("Failed to upload sync data: $responseBody")
                    logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
                    return false
                }
            }
    }

    private suspend fun reportSyncEvent(event: SyncEventStatus, message: String? = null) {
        withContext(NonCancellable) {
            try {
                val host = syncPreferences.clientHost().get()
                val apiKey = syncPreferences.clientAPIKey().get()
                val url = "$host/api/sync/event"

                val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
                val headers = headersBuilder.build()

                val bodyObj = SyncEvent(
                    event = event,
                    deviceName = android.os.Build.MODEL,
                    message = message,
                )

                val jsonBody = json.encodeToString(SyncEvent.serializer(), bodyObj)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = POST(
                    url = url,
                    headers = headers,
                    body = requestBody,
                )

                client.newCall(request).await().close()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to report sync event: ${e.message}" }
            }
        }
    }
}
