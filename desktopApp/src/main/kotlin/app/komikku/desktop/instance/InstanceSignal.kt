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
import java.net.SocketTimeoutException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.time.Duration

/**
 * KMK: the signal between two starts of the app on one data folder ([DesktopSingleInstance]): "show your window", or
 * "open this address" (an OAuth tracker redirect the OS handed the second start, `OAuthRedirectInbox`).
 *
 * The running instance listens on `127.0.0.1`, on a port the system picks, and writes the port and a random token to
 * the port file ([InstancePortFile]) with owner-only permissions. A second start sends one line, `SHOW <token>\n` or
 * `OPEN <token> <address>\n`; when the token matches, the running instance answers `OK\n` and shows its window or hands
 * the address on. Anything else (a wrong token, another command, an address that is empty, longer than
 * [MAX_ADDRESS_BYTES] or not printable ASCII, more than [MAX_REQUEST_BYTES] without a line end, no complete line within
 * the read timeout, which bounds the whole request and not each read) is dropped and the connection closed without an
 * answer. Connections are handled one at a time, so each slow client delays a real start by at most that timeout. The
 * address is not interpreted here: the receiver decides what it accepts.
 */
internal object InstanceSignal {

    /** The longest address an `OPEN` request carries. */
    const val MAX_ADDRESS_BYTES = 8192

    /** The longest request line read, line end included: `SHOW` is 70 bytes, `OPEN` 71 plus the address. */
    const val MAX_REQUEST_BYTES = MAX_ADDRESS_BYTES + 72

    private const val COMMAND = "SHOW "
    private const val OPEN = "OPEN "
    private val REPLY = "OK\n".toByteArray(Charsets.US_ASCII)

    val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    fun newToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun request(token: String): ByteArray = "$COMMAND$token\n".toByteArray(Charsets.US_ASCII)

    fun openRequest(token: String, address: String): ByteArray = "$OPEN$token $address\n".toByteArray(Charsets.US_ASCII)

    /** Whether [address] can travel in an `OPEN` request: 1 to [MAX_ADDRESS_BYTES] printable ASCII characters. */
    fun isSendable(address: String): Boolean = address.length in 1..MAX_ADDRESS_BYTES && address.all { it in '!'..'~' }

    /**
     * Asks the instance named in [portFile] to show its window; `true` once it answered. Never takes longer than
     * [timeout] (connect, send, answer); a missing, unreadable or stale port file is `false`.
     */
    fun requestShow(portFile: Path, timeout: Duration): Boolean = send(portFile, timeout, ::request)

    /** Like [requestShow], but asks it to open [address] ([isSendable]; otherwise `false` at once). */
    fun requestOpen(portFile: Path, address: String, timeout: Duration): Boolean =
        isSendable(address) && send(portFile, timeout) { openRequest(it, address) }

    private fun send(portFile: Path, timeout: Duration, request: (token: String) -> ByteArray): Boolean {
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
     * the line is longer. Bytes after the line end are ignored. [beforeRead] runs before each read (to bound the time).
     */
    fun readRequestLine(input: InputStream, max: Int = MAX_REQUEST_BYTES, beforeRead: () -> Unit = {}): ByteArray? {
        val buffer = ByteArray(max)
        var size = 0
        while (size < max) {
            beforeRead()
            val read = input.read(buffer, size, max - size)
            if (read < 0) return null
            for (i in size until size + read) {
                if (buffer[i] == '\n'.code.toByte()) return buffer.copyOf(i)
            }
            size += read
        }
        return null
    }

    /** A valid request: show the window, or open [address]. */
    sealed interface Request {
        data object Show : Request

        data class Open(val address: String) : Request
    }

    /** [line] (without its end) as a request of the instance whose token is [token]; `null` when it is not a valid one. */
    fun parseRequest(line: ByteArray, token: String): Request? {
        val show = "$COMMAND$token".toByteArray(Charsets.US_ASCII)
        if (MessageDigest.isEqual(line, show)) return Request.Show
        val open = "$OPEN$token ".toByteArray(Charsets.US_ASCII)
        if (line.size <= open.size || !MessageDigest.isEqual(line.copyOf(open.size), open)) return null
        val address = String(line, open.size, line.size - open.size, Charsets.US_ASCII)
        return Request.Open(address).takeIf { isSendable(address) }
    }

    /**
     * The running instance's side: accepts one connection at a time on its own daemon thread and calls [onShowRequest]
     * or [onOpenRequest] (on that thread) for each valid request. A request that comes before its callback is set is kept
     * (the addresses: the last [MAX_PENDING_ADDRESSES]) and delivered when it is set.
     */
    class Server(private val token: String, private val readTimeout: Duration) : AutoCloseable {

        private val socket = ServerSocket(0, BACKLOG, loopback)
        private val thread = Thread(::acceptLoop, "Komikku single instance").apply { isDaemon = true }
        private var pending = false
        private val pendingAddresses = ArrayDeque<String>()

        val port: Int get() = socket.localPort

        var onShowRequest: (() -> Unit)? = null
            set(value) {
                val replay = synchronized(this) {
                    field = value
                    (value != null && pending).also { if (it) pending = false }
                }
                if (replay) value?.let(::deliver)
            }

        var onOpenRequest: ((String) -> Unit)? = null
            set(value) {
                val replay = synchronized(this) {
                    field = value
                    if (value == null) emptyList() else pendingAddresses.toList().also { pendingAddresses.clear() }
                }
                value?.let { listener -> replay.forEach { address -> deliver { listener(address) } } }
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
            val deadline = System.nanoTime() + readTimeout.inWholeNanoseconds
            val line = try {
                readRequestLine(client.getInputStream()) {
                    val left = (deadline - System.nanoTime()) / 1_000_000
                    if (left <= 0) throw SocketTimeoutException("request not complete in time")
                    client.soTimeout = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            } catch (_: IOException) {
                null
            }
            val request = line?.let { parseRequest(it, token) }
            if (request == null) {
                xLogD("Single instance: ignored a request that was not a valid show or open request")
                return
            }
            if (socket.isClosed) return
            try {
                client.getOutputStream().apply {
                    write(REPLY)
                    flush()
                }
            } catch (_: IOException) {
            }
            when (request) {
                Request.Show -> {
                    xLogI("Single instance: another start on this data folder asked to show the window")
                    val listener = synchronized(this) { onShowRequest.also { if (it == null) pending = true } }
                    listener?.let(::deliver)
                }
                is Request.Open -> {
                    // Not the address itself: it may carry an authorization code
                    xLogI("Single instance: another start on this data folder asked to open an address")
                    val listener = synchronized(this) {
                        onOpenRequest.also {
                            if (it == null) {
                                pendingAddresses.addLast(request.address)
                                while (pendingAddresses.size > MAX_PENDING_ADDRESSES) pendingAddresses.removeFirst()
                            }
                        }
                    }
                    listener?.let { deliver { it(request.address) } }
                }
            }
        }

        private fun deliver(listener: () -> Unit) {
            try {
                listener()
            } catch (e: Exception) {
                xLogE("Single instance: handling another start's request failed", e)
            }
        }

        private companion object {
            const val BACKLOG = 8
            const val ACCEPT_RETRY_DELAY_MS = 100L
            const val MAX_PENDING_ADDRESSES = 4
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
