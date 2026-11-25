package app.komikku.sourcejs.store

import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostHttpRequest
import app.komikku.sourcejs.bridge.HostHttpResponse
import app.komikku.sourcejs.bridge.HttpBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.io.IOException

/**
 * A [HostHttp] answering from a URL → response table (404 otherwise), recording every request. The store and the
 * sources call it from other threads, so the state is in atomically replaced maps.
 *
 * @param enforceBodyCap whether a body above [HostHttpRequest.maxBodyBytes] fails like `DarwinHostHttp`'s; without it
 *   the store's own check is what refuses it.
 */
internal class FakeHostHttp(var enforceBodyCap: Boolean = true) : HostHttp {

    class Route(val status: Int, val body: ByteArray, val headers: List<Pair<String, String>> = emptyList())

    private val routes = MutableStateFlow<Map<String, Route>>(emptyMap())
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _requests = MutableStateFlow<List<HostHttpRequest>>(emptyList())

    /**
     * How long each request takes; one with a shorter [HostHttpRequest.timeoutMillis] fails with an `IOException`
     * `timeout` once that time passed, like `DarwinHostHttp`.
     */
    var delayMillis: Long = 0

    val requests: List<HostHttpRequest> get() = _requests.value

    val urls: List<String> get() = requests.map { it.url }

    fun route(url: String, body: String, status: Int = 200) {
        routes.update { it + (url to Route(status, body.encodeToByteArray())) }
        failures.update { it - url }
    }

    fun redirect(url: String, location: String, status: Int = 302) {
        routes.update { it + (url to Route(status, ByteArray(0), listOf("Location" to location))) }
    }

    /** Requests of [url] fail with an `IOException` (a network failure). */
    fun fail(url: String, message: String = "connection reset") {
        failures.update { it + (url to message) }
    }

    fun clearRequests() {
        _requests.value = emptyList()
    }

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        _requests.update { it + request }
        failures.value[request.url]?.let { throw IOException(it) }
        if (delayMillis > 0) {
            val timeout = request.timeoutMillis?.takeIf { it > 0 }
            if (timeout != null && timeout < delayMillis) {
                delay(timeout)
                throw IOException("timeout")
            }
            delay(delayMillis)
        }
        val route = routes.value[request.url] ?: return HostHttpResponse(404, emptyList(), "not found".encodeToByteArray())
        val cap = request.maxBodyBytes
        if (enforceBodyCap && cap != null && route.body.size > cap) throw IOException(HttpBridge.tooLargeMessage(cap))
        return HostHttpResponse(route.status, route.headers, route.body)
    }
}
