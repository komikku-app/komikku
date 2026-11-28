package app.komikku.desktop.track

import app.komikku.shared.track.OAuthRedirect
import exh.log.xLogE
import exh.log.xLogI
import exh.log.xLogW

/**
 * KMK: the OAuth trackers' app-scheme redirects (`komikku://myanimelist-auth?code=…`) the OS hands the desktop app: the
 * macOS open-URL event, or a start with the address as its argument (Windows / Linux), forwarded to the running app by
 * `DesktopSingleInstance`.
 *
 * A redirect is only taken while a login waits for it ([await]: the tracker login dialog of this app, after it opened
 * the authorize page). Any other one (no login in progress, another tracker, an unknown scheme or host) is ignored and
 * logged without its parameters, so a web page that opens a `komikku://` address cannot log the app in with a code of
 * its own (login CSRF).
 */
object OAuthRedirectInbox {

    /** The schemes the trackers redirect to: `komikku` (Android's `TrackLoginActivity`), `tachiyomisy` (MDList). */
    val SCHEMES = setOf("komikku", "tachiyomisy")

    /** The longest redirect taken; an AniList token makes one of about 1.1 KB. */
    const val MAX_LENGTH = 8192

    private val HOST = Regex("[a-z0-9-]{1,64}")

    private class Waiter(val scheme: String, val host: String, val receive: (String) -> Boolean)

    private val waiters = mutableListOf<Waiter>()

    /** Brings the window to the front after a redirect was taken (set by the window, called on the delivering thread). */
    @Volatile
    var onAccepted: (() -> Unit)? = null

    /** Whether [uri] looks like a tracker redirect: one of [SCHEMES], at most [MAX_LENGTH], printable ASCII only. */
    fun isRedirect(uri: String): Boolean {
        val text = uri.trim()
        if (text.length > MAX_LENGTH || text.any { it !in '!'..'~' }) return false
        return text.substringBefore("://", "").lowercase() in SCHEMES
    }

    /**
     * Hands the redirects to `[scheme]://[host]` to [receive] until the result is closed; [receive] returns whether it
     * took the redirect (`false` e.g. while a login is already running). A newer wait for the same address replaces it.
     */
    fun await(scheme: String, host: String, receive: (String) -> Boolean): AutoCloseable {
        val waiter = Waiter(scheme.lowercase(), host.lowercase(), receive)
        synchronized(waiters) {
            waiters.removeAll { it.scheme == waiter.scheme && it.host == waiter.host }
            waiters += waiter
        }
        return AutoCloseable { synchronized(waiters) { waiters.remove(waiter) } }
    }

    /** Delivers [uri] to the login waiting for it; `false` when it was ignored. Any thread. */
    fun deliver(uri: String): Boolean {
        val text = uri.trim()
        if (!isRedirect(text)) {
            xLogW("OAuth redirect: ignored an address that is not a tracker redirect")
            return false
        }
        val scheme = text.substringBefore("://").lowercase()
        val host = OAuthRedirect.parse(text).host?.lowercase()?.takeIf { HOST.matches(it) }
        if (host == null) {
            xLogW("OAuth redirect: ignored a $scheme:// address without a valid host")
            return false
        }
        val waiter = synchronized(waiters) { waiters.lastOrNull { it.scheme == scheme && it.host == host } }
        if (waiter == null) {
            xLogW("OAuth redirect: ignored $scheme://$host, no login for it is in progress")
            return false
        }
        val taken = try {
            waiter.receive(text)
        } catch (e: Exception) {
            xLogE("OAuth redirect: the login waiting for $scheme://$host failed to take it", e)
            false
        }
        if (!taken) {
            xLogW("OAuth redirect: ignored $scheme://$host, its login did not take it")
            return false
        }
        xLogI("OAuth redirect: $scheme://$host handed to its login")
        try {
            onAccepted?.invoke()
        } catch (e: Exception) {
            xLogE("OAuth redirect: could not bring the window to the front", e)
        }
        return true
    }
}
