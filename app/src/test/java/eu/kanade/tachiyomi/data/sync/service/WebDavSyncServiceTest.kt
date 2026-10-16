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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.preference.Preference

// KMK -->
class WebDavSyncServiceTest {

    private val protoBuf = ProtoBuf
    private val backup = Backup(emptyList(), backupCategories = listOf(BackupCategory("Test category")))
    private val backupBytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
    private val syncData = SyncData(backup = backup)
    private val notifier = mockk<SyncNotifier>(relaxed = true)
    private val preferences = mockk<SyncPreferences>()
    private val etagPreference = mockk<Preference<String>>()
    private val clients = mutableListOf<OkHttpClient>()
    private var cachedETag = ""

    @BeforeEach
    fun setUp() {
        mockkStatic("exh.log.LoggingKt")
        every { any<Any>().xLogD(any<String>(), *anyVararg()) } just Runs
        every { any<Any>().xLogI(any<String>()) } just Runs
        every { any<Any>().xLogE(any<String>(), any<Throwable>()) } just Runs

        every { preferences.webDavUrl().get() } returns "https://dav.example.test"
        every { preferences.webDavFolder().get() } returns ""
        every { preferences.webDavUsername().get() } returns "test-user"
        every { preferences.webDavPassword().get() } returns "test-password"
        every { preferences.uniqueDeviceID() } returns "test-device"
        every { preferences.lastSyncEtag() } returns etagPreference
        every { etagPreference.get() } answers { cachedETag }
        every { etagPreference.set(any()) } answers { cachedETag = firstArg() }
    }

    @AfterEach
    fun tearDown() {
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        unmockkStatic("exh.log.LoggingKt")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"abc-123\"", "\"opaque_ETag:+/=\""])
    fun `preserves server validators across consecutive syncs`(initialETag: String) = runTest {
        var serverETag = initialETag
        var uploads = 0
        val requests = mutableListOf<Request>()
        val service = service { request ->
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

        assertNotNull(service.doSync(syncData))
        assertEquals("\"uploaded-1\"", cachedETag)
        assertNotNull(service.doSync(syncData))

        assertEquals(listOf("GET", "PUT", "GET", "PUT"), requests.map { it.method })
        assertNull(requests[0].header("If-None-Match"))
        assertEquals(initialETag, requests[1].header("If-Match"))
        assertEquals("\"uploaded-1\"", requests[2].header("If-None-Match"))
        assertEquals("\"uploaded-1\"", requests[3].header("If-Match"))
        assertEquals("\"uploaded-2\"", cachedETag)
        assertEquals(2, uploads)
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc-123", "W/\"abc-123"])
    fun `fetches a fresh validator for legacy caches and accepts uploads without response ETags`(
        legacyETag: String,
    ) = runTest {
        cachedETag = legacyETag
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
        verify(exactly = 0) { etagPreference.set(any()) }
        verify(exactly = 0) { notifier.showSyncError(any()) }
    }

    @Test
    fun `preserves weak validators for conditional downloads`() = runTest {
        cachedETag = "W/\"weak-etag\""
        val requests = mutableListOf<Request>()
        val service = service { request ->
            requests += request
            when (request.method) {
                "GET" -> response(request, 304)
                // A weak validator cannot satisfy the strong comparison required by If-Match.
                "PUT" -> response(request, 412)
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNull(service.doSync(syncData))

        assertEquals("W/\"weak-etag\"", requests[0].header("If-None-Match"))
        assertEquals("W/\"weak-etag\"", requests[1].header("If-Match"))
        verify(exactly = 1) { notifier.showSyncError(match { it.contains("Sync conflict detected") }) }
    }

    @Test
    fun `a rejected upload reports failure without returning a backup to restore`() = runTest {
        cachedETag = "\"cached-etag\""
        val service = service { request ->
            when (request.method) {
                "GET" -> response(request, 200, "\"remote-etag\"", backupBytes)
                "PUT" -> response(request, 412)
                else -> error("Unexpected method: ${request.method}")
            }
        }

        assertNull(service.doSync(syncData))

        assertEquals("\"cached-etag\"", cachedETag)
        verify(exactly = 0) { etagPreference.set(any()) }
        verify(exactly = 1) { notifier.showSyncError(match { it.contains("Sync conflict detected") }) }
        verify(exactly = 0) { notifier.showSyncSuccess(any()) }
    }

    private fun service(respond: (Request) -> Response): WebDavSyncService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request()) }
            .build()
        clients += client
        return WebDavSyncService(mockk<Context>(), Json, preferences, notifier, protoBuf, client)
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
