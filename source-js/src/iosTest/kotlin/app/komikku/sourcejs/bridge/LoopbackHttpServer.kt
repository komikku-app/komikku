package app.komikku.sourcejs.bridge

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSThread
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.concurrent.AtomicInt
import platform.posix.close as closeFd

/**
 * A minimal HTTP/1.1 server on `127.0.0.1` for the simulator tests: plain POSIX sockets, one `NSThread` accepting and
 * one per connection, one request per connection (`Connection: close`). Ktor's CIO server did not start inside the
 * simulator test process (every start timed out), so this stays small and dependency-free.
 */
@OptIn(ExperimentalForeignApi::class)
internal class LoopbackHttpServer(private val handler: (Request) -> Response) {

    class Request(val method: String, val target: String, val headers: Map<String, String>, val body: ByteArray)

    class Response(
        val status: Int,
        val headers: List<Pair<String, String>> = emptyList(),
        val body: ByteArray = ByteArray(0),
        /** Seconds to wait before answering. */
        val delaySeconds: Double = 0.0,
    )

    private val listener: Int = socket(AF_INET, SOCK_STREAM, 0)
    private val closed = AtomicInt(0)

    /** The bound port. */
    val port: Int

    val baseUrl: String get() = "http://127.0.0.1:$port"

    init {
        check(listener >= 0) { "socket failed" }
        port = memScoped {
            val one = alloc<IntVar>().apply { value = 1 }
            setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
            val address = alloc<sockaddr_in>()
            address.sin_len = sizeOf<sockaddr_in>().convert()
            address.sin_family = AF_INET.convert()
            address.sin_port = 0u
            // 127.0.0.1 in network byte order, read as a little-endian UInt.
            address.sin_addr.s_addr = 0x0100007Fu
            check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) { "bind failed" }
            check(listen(listener, 16) == 0) { "listen failed" }
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0) { "getsockname failed" }
            val networkPort = address.sin_port.toInt()
            ((networkPort and 0xFF) shl 8) or ((networkPort shr 8) and 0xFF)
        }
        NSThread { acceptLoop() }.start()
    }

    fun close() {
        if (closed.compareAndSet(0, 1)) closeFd(listener)
    }

    private fun acceptLoop() {
        while (closed.value == 0) {
            val connection = accept(listener, null, null)
            if (connection < 0) return
            NSThread { serve(connection) }.start()
        }
    }

    private fun serve(connection: Int) {
        try {
            memScoped {
                val one = alloc<IntVar>().apply { value = 1 }
                // A client that went away must not kill the test process with SIGPIPE.
                setsockopt(connection, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
            }
            val request = readRequest(connection) ?: return
            val response = handler(request)
            if (response.delaySeconds > 0) NSThread.sleepForTimeInterval(response.delaySeconds)
            val head = buildString {
                append("HTTP/1.1 ${response.status} X\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Length: ${response.body.size}\r\nConnection: close\r\n\r\n")
            }
            writeAll(connection, head.encodeToByteArray() + response.body)
        } finally {
            closeFd(connection)
        }
    }

    private fun readRequest(connection: Int): Request? {
        var data = ByteArray(0)
        val chunk = ByteArray(8192)
        fun readMore(): Boolean {
            val count = chunk.usePinned { recv(connection, it.addressOf(0), chunk.size.convert(), 0).toInt() }
            if (count <= 0) return false
            data += chunk.copyOf(count)
            return true
        }
        var headerEnd = -1
        while (headerEnd < 0) {
            if (!readMore()) return null
            headerEnd = data.decodeToString().indexOf("\r\n\r\n")
        }
        // The head is ASCII, so its string index is its byte index.
        val lines = data.decodeToString(0, headerEnd).split("\r\n")
        val (method, target) = lines.first().split(" ")
        val headers = lines.drop(1).associate { line ->
            line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyStart = headerEnd + 4
        while (data.size < bodyStart + length) {
            if (!readMore()) return null
        }
        return Request(method, target, headers, data.copyOfRange(bodyStart, bodyStart + length))
    }

    private fun writeAll(connection: Int, bytes: ByteArray) {
        var offset = 0
        bytes.usePinned { pinned ->
            while (offset < bytes.size) {
                val sent = send(connection, pinned.addressOf(offset), (bytes.size - offset).convert(), 0).toInt()
                if (sent <= 0) return
                offset += sent
            }
        }
    }
}
