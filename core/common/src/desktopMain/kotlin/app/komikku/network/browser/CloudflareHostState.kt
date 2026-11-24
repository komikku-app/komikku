package app.komikku.network.browser

import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: what the desktop Cloudflare interceptor remembers per host between requests (P7 review):
 *
 * - **A failed solve**, for [failureMemory]: requests that queued behind it on the host's lock and the ones right after
 *   it fail at once with the same reason instead of each running the browser again (up to its timeout each). A new
 *   clearance cookie in the jar (the remembered failure is bound to the clearance it saw) or [clearFailure] (a manual
 *   "Open in WebView" save) ends it early, and [clearFailures] (another solver address) ends all of them. Failures of
 *   the backend itself (`BrowserUnavailableException`: not configured, unreachable, an error answer) are not recorded.
 * - **The User-Agent a clearance belongs to**: Cloudflare binds `cf_clearance` to the browser's User-Agent, so the
 *   interceptor sends the solver's (or the pasted) User-Agent to that host while that clearance is in the jar, also when
 *   the source sets its own User-Agent header. Bound to a hash of the clearance value, so a newer clearance (the site's,
 *   another solve) ends the override without anyone removing it. Kept in [store] (the desktop app persists it in a
 *   preference; tests and the default keep it in memory).
 */
class CloudflareHostState(
    private val store: UserAgentOverrideStore = UserAgentOverrideStore.InMemory(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val failureMemory: Duration = FAILURE_MEMORY,
) {

    /** A failed solve of a host: the exception it ended with and the clearance in the jar afterwards. */
    private class Failure(val error: CloudflareBypassException, val clearance: String?, val at: Long)

    private val failures = ConcurrentHashMap<String, Failure>()

    @Volatile
    private var overrides: Map<String, UserAgentOverride> = store.load()

    /**
     * The failure of [host]'s last solve when it is younger than [failureMemory] and the jar still holds the clearance
     * it saw ([currentClearance]); a new [CloudflareBypassException] with its reason and message (the original as the
     * cause), or `null`.
     */
    fun recentFailure(host: String, currentClearance: String?): CloudflareBypassException? {
        val failure = failures[host] ?: return null
        if (clock() - failure.at >= failureMemory.inWholeMilliseconds || failure.clearance != currentClearance) {
            failures.remove(host, failure)
            return null
        }
        return CloudflareBypassException(
            host = host,
            reason = failure.error.reason,
            message = failure.error.message.orEmpty(),
            cause = failure.error,
        )
    }

    fun recordFailure(host: String, error: CloudflareBypassException, clearance: String?) {
        failures[host] = Failure(error, clearance, clock())
    }

    fun clearFailure(host: String) {
        failures.remove(host)
    }

    /** Forgets every host's failed solve (P8 review: the solver's address changed, its failures say nothing now). */
    fun clearFailures() {
        failures.clear()
    }

    /**
     * The User-Agent to send to [host]: its override when that is bound to the jar's current clearance for the request
     * ([currentClearance], read only when there are overrides) or to none; otherwise another host's override bound to
     * that same clearance (a site-wide `cf_clearance` used by the site's other hosts); else `null` (the request's own).
     */
    fun userAgentFor(host: String, currentClearance: () -> String?): String? {
        val all = overrides
        if (all.isEmpty()) return null
        val clearance = currentClearance()?.let(::hash)
        all[host]?.let { override ->
            if (override.clearanceHash == null || override.clearanceHash == clearance) return override.userAgent
        }
        if (clearance == null) return null
        return all.values.firstOrNull { it.clearanceHash == clearance }?.userAgent
    }

    /** The override stored for [host], whatever its clearance. */
    fun userAgentOverride(host: String): UserAgentOverride? = overrides[host]

    /**
     * Sends [userAgent] to [host] while the jar holds [clearance] (`null`: whatever clearance, until it is removed or
     * replaced).
     */
    fun setUserAgent(host: String, userAgent: String, clearance: String?) {
        update { it + (host to UserAgentOverride(userAgent, clearance?.let(::hash))) }
    }

    fun removeUserAgent(host: String) {
        if (host in overrides) update { it - host }
    }

    /** Every stored override by host (P12: listed in Settings → Advanced, where they can be removed). */
    fun userAgentOverrides(): Map<String, UserAgentOverride> = overrides

    /** Removes every host's override (P12: Settings → Advanced). */
    fun clearUserAgents() {
        if (overrides.isNotEmpty()) update { emptyMap() }
    }

    private fun update(change: (Map<String, UserAgentOverride>) -> Map<String, UserAgentOverride>) {
        synchronized(this) {
            overrides = change(overrides)
            try {
                store.save(overrides)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Could not store the Cloudflare User-Agent overrides" }
            }
        }
    }

    companion object {
        /** How long a failed solve is remembered (P7 review: 30–60 s). */
        val FAILURE_MEMORY: Duration = 45.seconds

        /** A clearance cookie's value is not kept next to the User-Agent, only its hash. */
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** A User-Agent sent to a host while the jar holds the clearance whose hash is [clearanceHash] (`null`: any). */
@Serializable
data class UserAgentOverride(val userAgent: String, val clearanceHash: String?)

/** Where [CloudflareHostState] keeps its User-Agent overrides. */
interface UserAgentOverrideStore {
    fun load(): Map<String, UserAgentOverride>

    fun save(overrides: Map<String, UserAgentOverride>)

    class InMemory : UserAgentOverrideStore {
        private var saved: Map<String, UserAgentOverride> = emptyMap()

        override fun load(): Map<String, UserAgentOverride> = saved

        override fun save(overrides: Map<String, UserAgentOverride>) {
            saved = overrides
        }
    }

    /** JSON in a string preference (the desktop's `desktop_cloudflare_user_agents`). An unreadable value is empty. */
    class InPreference(private val preference: Preference<String>) : UserAgentOverrideStore {
        override fun load(): Map<String, UserAgentOverride> = try {
            preference.get().takeIf { it.isNotBlank() }?.let { json.decodeFromString<Map<String, UserAgentOverride>>(it) }
                .orEmpty()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Ignoring unreadable Cloudflare User-Agent overrides" }
            emptyMap()
        }

        override fun save(overrides: Map<String, UserAgentOverride>) {
            preference.set(json.encodeToString(overrides))
        }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
}

/** The [CloudflareHostState] the desktop's interceptors and "Open in WebView" share (one per app). */
object CloudflareHosts {
    @Volatile
    var state: CloudflareHostState = CloudflareHostState()
}
