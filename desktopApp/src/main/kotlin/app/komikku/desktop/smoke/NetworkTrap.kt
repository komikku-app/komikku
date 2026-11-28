package app.komikku.desktop.smoke

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The UI smoke mode's network trap, installed as the process-wide [ProxySelector] before anything else starts (ANSWER
 * 011, condition 5). OkHttp (every client the app builds, FlareSolverr's too), `URLConnection` and plain sockets ask it
 * for every connection: it records the address and the call site and sends the connection to a closed port on the
 * loopback address, where it fails. Any [attempts] fails the run.
 */
internal class NetworkTrap(private val closedPort: Int = findClosedPort()) : ProxySelector() {

    data class Attempt(val uri: String, val thread: String, val callSite: List<String>)

    private val recorded = CopyOnWriteArrayList<Attempt>()

    val attempts: List<Attempt> get() = recorded.toList()

    private var previous: ProxySelector? = null

    fun install() {
        previous = getDefault()
        setDefault(this)
    }

    /** Tests only: puts the previous selector back. */
    fun uninstall() {
        setDefault(previous)
    }

    override fun select(uri: URI): List<Proxy> {
        recorded += Attempt(uri.toString(), Thread.currentThread().name, callSite(Throwable().stackTrace))
        val closed = InetSocketAddress(InetAddress.getLoopbackAddress(), closedPort)
        // Plain sockets ask with `socket://host:port` and only follow a SOCKS proxy
        val type = if (uri.scheme.equals("socket", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return listOf(Proxy(type, closed))
    }

    override fun connectFailed(uri: URI?, address: SocketAddress?, error: IOException?) = Unit

    companion object {

        /** Stack frames shown per attempt: the first ones outside the JDK, the network libraries and this class. */
        private const val CALL_SITE_FRAMES = 12

        private val LIBRARY_PREFIXES = listOf(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "kotlin.",
            "kotlinx.coroutines.",
            "okhttp3.",
            "okio.",
        )

        private val OWN_CLASS = NetworkTrap::class.java.name

        private fun isOwnOrLibrary(className: String) =
            className == OWN_CLASS || className.startsWith("$OWN_CLASS$") || LIBRARY_PREFIXES.any { className.startsWith(it) }

        internal fun callSite(stack: Array<StackTraceElement>): List<String> {
            val app = stack.filterNot { frame -> isOwnOrLibrary(frame.className) }
            return (app.ifEmpty { stack.toList() }).take(CALL_SITE_FRAMES).map(StackTraceElement::toString)
        }

        /** A loopback port nothing listens on (bound once to get a free one, then closed). */
        private fun findClosedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    }
}
