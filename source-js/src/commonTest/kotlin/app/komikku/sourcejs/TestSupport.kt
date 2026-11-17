package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostHttpRequest
import app.komikku.sourcejs.bridge.HostHttpResponse
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.InMemoryKeyValueStore
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.bridge.KeyValueHostPreferences

internal val testSource = JsSourceDescriptor(
    id = 652112892,
    name = "Test Source",
    lang = "en",
    baseUrl = "https://example.org",
    apiUrl = "https://api.example.org",
    sourceCodeUrl = "https://raw.example.org/javascript/manga/src/en/test.js",
    version = "0.0.1",
    dateFormat = "MMMM dd, yyyy",
    dateFormatLocale = "en_us",
    additionalParams = "extra",
    notes = "some notes",
)

/** [HostHttp] fake: answers from [handler] and records the requests. */
internal class FakeHostHttp(
    private val handler: (HostHttpRequest) -> HostHttpResponse = { HostHttpResponse(404, emptyList(), ByteArray(0)) },
) : HostHttp {
    val requests = mutableListOf<HostHttpRequest>()

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        requests += request
        return handler(request)
    }
}

internal class RecordingLog : HostLog {
    val lines = mutableListOf<Pair<HostLog.Level, String>>()

    override fun log(level: HostLog.Level, source: JsSourceDescriptor, message: String) {
        lines += level to message
    }
}

internal class TestHost(
    http: HostHttp = FakeHostHttp(),
    limits: JsHostLimits = JsHostLimits(callTimeoutMillis = 20_000),
) {
    val store = InMemoryKeyValueStore()
    val log = RecordingLog()
    val services = JsHostServices(
        http = http,
        preferences = KeyValueHostPreferences.provider(store),
        log = log,
    )
    val host = JsExtensionHost(services, limits = limits)

    fun extension(code: String, source: JsSourceDescriptor = testSource) = host.extension(source, code)
}

/** Wraps [body] (members of `DefaultExtension`) in an extension file the way Mangayomi's files look. */
internal fun provider(body: String, header: String = ""): String = """
const mangayomiSources = [{ "name": "Test Source", "lang": "en", "id": 652112892 }];
$header
class DefaultExtension extends MProvider {
$body
}
"""
