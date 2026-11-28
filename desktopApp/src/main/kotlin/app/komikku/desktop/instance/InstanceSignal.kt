package app.komikku.desktop.instance

import exh.log.xLogD
import exh.log.xLogE
import exh.log.xLogI
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.time.Duration

/**
 * KMK: the "show your window" signal between two starts of the app on one data folder ([DesktopSingleInstance]).
 *
 * The running instance listens on `127.0.0.1`, on a port the system picks, and writes the port and a random token to
 * the port file ([InstancePortFile]) with owner-only permissions. A second start sends one line, `SHOW <token>\n`;
 * when it matches exactly, the running instance answers `OK\n` and shows its window. Anything else (a wrong token,
 * another command, more than [MAX_REQUEST_BYTES] without a line end, nothing within the read timeout) is dropped and
 * the connection closed without an answer. The request is never parsed further: it means "show the window" and nothing
 * else.
 */
internal object InstanceSignal {

    /** The longest request line read, line end included; the real one is 70 bytes. */
    const val MAX_REQUEST_BYTES = 128

    private const val COMMAND = "SHOW "
    private val REPLY = "OK\n".toByteArray(Charsets.US_ASCII)

    val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    fun newToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun request(token: String): ByteArray = "$COMMAND$token\n".toByteArray(Charsets.US_ASCII)

    /**
     * Asks the instance named in [portFile] to show its window; `true` once it answered. Never takes longer than
     * [timeout] (connect, send, answer); a missing, unreadable or stale port file is `false`.
     */
    fun requestShow(portFile: Path, timeout: Duration): Boolean {
        val endpoint = InstancePortFile.read(portFile) ?: return false
        val millis = timeout.inWholeMilliseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(loopback, endpoint.port), millis)
                socket.soTimeout = millis
                socket.getOutputStream().apply {
                    write(request(endpoint.token))
                    flush()
                }
                socket.getInputStream().readNBytes(REPLY.size).contentEquals(REPLY)
            }
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Reads one line of at most [max] bytes from [input], without the line end; `null` when the stream ends first or
     * the line is longer. Bytes after the line end are ignored.
     */
    fun readRequestLine(input: InputStream, max: Int = MAX_REQUEST_BYTES): ByteArray? {
        val buffer = ByteArray(max)
        var size = 0
        while (size < max) {
            val read = input.read(buffer, size, max - size)
            if (read < 0) return null
            for (i in size until size + read) {
                if (buffer[i] == '\n'.code.toByte()) return buffer.copyOf(i)
            }
            size += read
        }
        return null
    }

    /**
     * The running instance's side: accepts one connection at a time on its own daemon thread and calls [onShowRequest]
     * (on that thread) for each valid request. A request that comes before [onShowRequest] is set is kept and delivered
     * when it is set.
     */
    class Server(token: String, private val readTimeout: Duration) : AutoCloseable {

        private val socket = ServerSocket(0, BACKLOG, loopback)
        private val expected = "$COMMAND$token".toByteArray(Charsets.US_ASCII)
        private val thread = Thread(::acceptLoop, "Komikku single instance").apply { isDaemon = true }
        private var pending = false

        val port: Int get() = socket.localPort

        var onShowRequest: (() -> Unit)? = null
            set(value) {
                val replay = synchronized(this) {
                    field = value
                    (value != null && pending).also { if (it) pending = false }
                }
                if (replay) value?.let(::deliver)
            }

        fun start(): Server = apply { thread.start() }

        override fun close() {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }

        private fun acceptLoop() {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (socket.isClosed) return
                    xLogE("Single instance: accepting a connection failed", e)
                    Thread.sleep(ACCEPT_RETRY_DELAY_MS)
                    continue
                }
                client.use(::handle)
            }
        }

        private fun handle(client: Socket) {
            val line = try {
                client.soTimeout = readTimeout.inWholeMilliseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
                readRequestLine(client.getInputStream())
            } catch (_: IOException) {
                null
            }
            if (line == null || !MessageDigest.isEqual(line, expected)) {
                xLogD("Single instance: ignored a request that was not a valid show request")
                return
            }
            try {
                client.getOutputStream().apply {
                    write(REPLY)
                    flush()
                }
            } catch (_: IOException) {
            }
            xLogI("Single instance: another start on this data folder asked to show the window")
            val listener = synchronized(this) { onShowRequest.also { if (it == null) pending = true } }
            listener?.let(::deliver)
        }

        private fun deliver(listener: () -> Unit) {
            try {
                listener()
            } catch (e: Exception) {
                xLogE("Single instance: showing the window failed", e)
            }
        }

        private companion object {
            const val BACKLOG = 8
            const val ACCEPT_RETRY_DELAY_MS = 100L
        }
    }
}

/**
 * KMK: the port file next to the lock file: two lines, the port and the token ([InstanceSignal]). Written atomically
 * (a temporary file with owner-only permissions, [OwnerOnlyFiles], moved over the old one) and read with a size limit.
 */
internal object InstancePortFile {

    private const val MAX_BYTES = 256
    private val TOKEN = Regex("[0-9a-f]{64}")

    data class Endpoint(val port: Int, val token: String)

    fun write(file: Path, endpoint: Endpoint) {
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.deleteIfExists(temporary)
        OwnerOnlyFiles.create(temporary)
        Files.write(
            temporary,
            "${endpoint.port}\n${endpoint.token}\n".toByteArray(Charsets.US_ASCII),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun read(file: Path): Endpoint? {
        val bytes = try {
            Files.newInputStream(file).use { it.readNBytes(MAX_BYTES + 1) }
        } catch (_: IOException) {
            return null
        }
        if (bytes.size > MAX_BYTES) return null
        val lines = String(bytes, Charsets.US_ASCII).lines()
        val port = lines.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val token = lines.getOrNull(1)?.trim()?.takeIf { TOKEN.matches(it) } ?: return null
        return Endpoint(port, token)
    }
}
