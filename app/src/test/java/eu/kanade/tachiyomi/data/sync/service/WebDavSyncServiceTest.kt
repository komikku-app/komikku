package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import exh.log.xLogD
import exh.log.xLogE
import exh.log.xLogI
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR

// KMK -->
class WebDavSyncServiceTest {

    private val protoBuf = ProtoBuf
    private val backup = Backup(emptyList(), backupCategories = listOf(BackupCategory("Test category")))
    private val backupBytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
    private val syncData = SyncData(backup = backup)
    private val context = mockk<Context>()
    private val notifier = mockk<SyncNotifier>(relaxed = true)
    private val preferenceStore = mockk<PreferenceStore>()
    private val preferences = SyncPreferences(preferenceStore)
    private val storedStrings = mutableMapOf<String, Preference<String>>()
    private val clients = mutableListOf<OkHttpClient>()
    private val weakETagError = "A strong ETag is required to safely update the remote backup."

    @BeforeEach
    fun setUp() {
        mockkStatic("exh.log.LoggingKt")
        every { any<Any>().xLogD(any<String>(), *anyVararg()) } just Runs
        every { any<Any>().xLogI(any<String>()) } just Runs
        every { any<Any>().xLogE(any<String>(), any<Throwable>()) } just Runs

        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(KMR.strings.webdav_weak_etag_error) } returns weakETagError

        every { preferenceStore.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val defaultValue = secondArg<String>()
            storedStrings.getOrPut(key) { InMemoryPreference(key, null, defaultValue) }
        }
        preferences.webDavUrl().set("https://dav.example.test")
        preferences.webDavFolder().set("")
        preferences.webDavUsername().set("test-user")
        preferences.webDavPassword().set("test-password")
    }

    @AfterEach
    fun tearDown() {
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        unmockkStatic("exh.log.LoggingKt")
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"abc-123\"", "\"opaque_ETag:+/=\""])
    fun `preserves server validators across consecutive syncs`(initialETag: String) = runTest {
        var serverETag = initialETag
        var uploads = 0
        val requests = mutableListOf<Request>()
        val respond: (Request) -> Response = { request ->
            requests += request
            when (request.method) {
                "GET" -> if (request.header("If-None-Match") == serverETag) {
                    response(request, 304)
                } else {
                    response(request, 200, serverETag, backupBytes)
                }
                "PUT" -> if (request.header("If-Match") == serverETag) {
                    uploads++
                    serverETag = "\"uploaded-$uploads\""
                    response(request, 204, serverETag)
                } else {
                    response(request, 412)
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNotNull(service(respond).doSync(syncData))
        assertNotNull(service(respond).doSync(syncData))

        assertEquals(listOf("GET", "PUT", "GET", "PUT"), requests.map { it.method })
        assertNull(requests[0].header("If-None-Match"))
        assertEquals(initialETag, requests[1].header("If-Match"))
        assertEquals("\"uploaded-1\"", requests[2].header("If-None-Match"))
        assertEquals("\"uploaded-1\"", requests[3].header("If-Match"))
        assertEquals(2, uploads)
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc-123", "W/\"abc-123", "\"fresh-etag\""])
    fun `fetches a fresh validator for legacy caches and accepts uploads without response ETags`(
        legacyETag: String,
    ) = runTest {
        preferences.lastSyncEtag().set(legacyETag)
        val serverETag = "\"fresh-etag\""
        val requests = mutableListOf<Request>()
        val service = service { request ->
            requests += request
            when (request.method) {
                "GET" -> response(request, 200, serverETag, backupBytes)
                "PUT" -> if (request.header("If-Match") == serverETag) {
                    response(request, 204)
                } else {
                    response(request, 412)
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNotNull(service.doSync(syncData))

        assertNull(requests[0].header("If-None-Match"))
        assertEquals(serverETag, requests[1].header("If-Match"))
        assertEquals(legacyETag, preferences.lastSyncEtag().get())
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["server", "folder", "account"])
    fun `merges the remote backup after changing the WebDAV resource even with the same ETag`(
        changedSetting: String,
    ) = runTest {
        val serverETag = "\"same-etag\""
        val remoteBackup = Backup(emptyList(), backupCategories = listOf(BackupCategory("Remote only")))
        val remoteBytes = protoBuf.encodeToByteArray(Backup.serializer(), remoteBackup)
        val requests = mutableListOf<Request>()
        var changed = false
        var uploadedBackup: Backup? = null
        val respond: (Request) -> Response = { request ->
            requests += request
            when (request.method) {
                "MKCOL" -> response(request, 405)
                "GET" -> if (request.header("If-None-Match") == serverETag) {
                    response(request, 304)
                } else {
                    response(request, 200, serverETag, if (changed) remoteBytes else backupBytes)
                }
                "PUT" -> {
                    val buffer = Buffer()
                    request.body!!.writeTo(buffer)
                    uploadedBackup = protoBuf.decodeFromByteArray(Backup.serializer(), buffer.readByteArray())
                    response(request, 204, serverETag)
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNotNull(service(respond).doSync(syncData))
        when (changedSetting) {
            "server" -> preferences.webDavUrl().set("https://other.example.test")
            "folder" -> preferences.webDavFolder().set("another-folder")
            "account" -> preferences.webDavUsername().set("other-user")
        }
        changed = true

        val result = service(respond).doSync(syncData)

        assertEquals(listOf(null, null), requests.filter { it.method == "GET" }.map { it.header("If-None-Match") })
        val expectedCategories = setOf("Test category", "Remote only")
        assertEquals(expectedCategories, result?.backupCategories?.map { it.name }?.toSet())
        assertEquals(expectedCategories, uploadedBackup?.backupCategories?.map { it.name }?.toSet())
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "W/\"weak-etag\""])
    fun `fetches the remote backup again when a successful upload returns no strong ETag`(
        uploadETag: String,
    ) = runTest {
        val serverETag = "\"strong-etag\""
        val requests = mutableListOf<Request>()
        var uploads = 0
        val respond: (Request) -> Response = { request ->
            requests += request
            when (request.method) {
                "GET" -> if (request.header("If-None-Match") == serverETag) {
                    response(request, 304)
                } else {
                    response(request, 200, serverETag, backupBytes)
                }
                "PUT" -> {
                    uploads++
                    response(request, 204, if (uploads == 1) serverETag else uploadETag.ifEmpty { null })
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        repeat(3) { assertNotNull(service(respond).doSync(syncData)) }

        assertEquals(
            listOf(null, serverETag, null),
            requests.filter { it.method == "GET" }.map { it.header("If-None-Match") },
        )
        assertEquals(List(3) { serverETag }, requests.filter { it.method == "PUT" }.map { it.header("If-Match") })
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @Test
    fun `a weak download ETag reports the unsupported server without uploading`() = runTest {
        val requests = mutableListOf<Request>()
        val service = service { request ->
            requests += request
            response(request, 200, "W/\"weak-etag\"", backupBytes)
        }

        assertNull(service.doSync(syncData))

        assertEquals(listOf("GET", "GET"), requests.map { it.method })
        assertEquals("identity", requests.last().header("Accept-Encoding"))
        assertNull(requests.last().header("If-None-Match"))
        verify(exactly = 1) { notifier.showSyncError(weakETagError) }
        verify(exactly = 0) { notifier.showSyncSuccess(any()) }
    }

    @Test
    fun `a weak ETag on a not modified response also stops the upload`() = runTest {
        val requests = mutableListOf<Request>()
        var uploaded = false
        val respond: (Request) -> Response = { request ->
            requests += request
            when (request.method) {
                "GET" -> if (request.header("Accept-Encoding") == "identity") {
                    response(request, 200, "W/\"etag\"", backupBytes)
                } else if (uploaded) {
                    response(request, 304, "W/\"etag\"")
                } else {
                    response(request, 200, "\"etag\"", backupBytes)
                }
                "PUT" -> {
                    uploaded = true
                    response(request, 204, "\"etag\"")
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNotNull(service(respond).doSync(syncData))
        assertNull(service(respond).doSync(syncData))

        assertEquals(listOf("GET", "PUT", "GET", "GET"), requests.map { it.method })
        verify(exactly = 1) { notifier.showSyncError(weakETagError) }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 304])
    fun `recovers from a weak ETag by merging a fresh uncompressed backup before uploading`(
        weakResponseCode: Int,
    ) = runTest {
        val remoteBackup = Backup(emptyList(), backupCategories = listOf(BackupCategory("Remote only")))
        val remoteBytes = protoBuf.encodeToByteArray(Backup.serializer(), remoteBackup)
        val requests = mutableListOf<Request>()
        var uploads = 0
        var uploadedBackup: Backup? = null
        val respond: (Request) -> Response = { request ->
            requests += request
            when (request.method) {
                "GET" -> when {
                    uploads == 0 -> response(request, 200, "\"original\"", backupBytes)
                    request.header("Accept-Encoding") == "identity" -> {
                        response(request, 200, "\"fresh\"", remoteBytes)
                    }
                    else -> response(request, weakResponseCode, "W/\"original\"", backupBytes)
                }
                "PUT" -> {
                    val buffer = Buffer()
                    request.body!!.writeTo(buffer)
                    uploadedBackup = protoBuf.decodeFromByteArray(Backup.serializer(), buffer.readByteArray())
                    uploads++
                    response(request, 204, if (uploads == 1) "\"original\"" else "\"fresh\"")
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNotNull(service(respond).doSync(syncData))
        val result = service(respond).doSync(syncData)

        assertEquals(listOf("GET", "PUT", "GET", "GET", "PUT"), requests.map { it.method })
        assertEquals("identity", requests[3].header("Accept-Encoding"))
        assertNull(requests[3].header("If-None-Match"))
        assertEquals("\"fresh\"", requests[4].header("If-Match"))
        val expectedCategories = setOf("Test category", "Remote only")
        assertEquals(expectedCategories, result?.backupCategories?.map { it.name }?.toSet())
        assertEquals(expectedCategories, uploadedBackup?.backupCategories?.map { it.name }?.toSet())
        assertEquals(2, uploads)
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @Test
    fun `a rejected upload reports failure without returning a backup to restore`() = runTest {
        preferences.lastSyncEtag().set("\"cached-etag\"")
        val service = service { request ->
            when (request.method) {
                "GET" -> response(request, 200, "\"remote-etag\"", backupBytes)
                "PUT" -> response(request, 412)
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNull(service.doSync(syncData))

        assertEquals("\"cached-etag\"", preferences.lastSyncEtag().get())
        verify(exactly = 1) { notifier.showSyncError(match { it.contains("Sync conflict detected") }) }
        verify(exactly = 0) { notifier.showSyncSuccess(any()) }
    }

    private fun service(respond: (Request) -> Response): WebDavSyncService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request()) }
            .build()
        clients += client
        return WebDavSyncService(context, Json, preferences, notifier, protoBuf, client)
    }

    private fun response(
        request: Request,
        code: Int,
        etag: String? = null,
        body: ByteArray = byteArrayOf(),
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Test response")
        .body(body.toResponseBody("application/octet-stream".toMediaType()))
        .apply { if (etag != null) header("ETag", etag) }
        .build()
}
// KMK <--
