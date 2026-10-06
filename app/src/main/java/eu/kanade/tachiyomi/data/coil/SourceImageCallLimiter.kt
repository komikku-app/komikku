package eu.kanade.tachiyomi.data.coil

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

// KMK -->
/**
 * Keeps image requests to a source's own API host from queueing ahead of its API calls.
 *
 * OkHttp runs a limited number of calls per host and queues the rest in order. A grid of covers
 * served from the API host fills that queue, and a chapter-list call made afterwards waits for
 * every image ahead of it. Here the extra images wait in this limiter instead, so OkHttp's queue
 * stays short and API calls take the next free slot. Images on any other host (CDNs, the usual
 * case) never touch the limiter.
 *
 * Each image holds its permit only until its response headers arrive, the same moment OkHttp
 * frees its own slot, and never more permits than OkHttp's per-host limit. So images load
 * exactly as fast as without the limiter.
 */
internal object SourceImageCallLimiter {

    /** OkHttp's default per-host limit, used when the source's client can't be read. */
    private const val DEFAULT_MAX_REQUESTS_PER_HOST = 5

    /**
     * Shared rather than local because Coil runs each fetcher independently. Keys are only
     * created for hosts matching a source's base URL, so there is at most one per source.
     */
    private val semaphores = ConcurrentHashMap<String, Semaphore>()

    /**
     * Runs [call] under a permit for [url]'s host when it is [source]'s API host. Requests on
     * other hosts, and cache-only requests ([networkRead] false), run unlimited.
     */
    suspend fun execute(
        source: Source?,
        url: String?,
        networkRead: Boolean,
        call: suspend () -> Response,
    ): Response {
        val httpSource = source as? HttpSource
        if (!networkRead || httpSource == null) return call()
        val host = sharedHost(runCatching { httpSource.baseUrl }.getOrNull(), url) ?: return call()
        val semaphore = semaphores.computeIfAbsent(host) {
            val limit = runCatching { httpSource.client.dispatcher.maxRequestsPerHost }
                .getOrDefault(DEFAULT_MAX_REQUESTS_PER_HOST)
            Semaphore(limit.coerceAtLeast(1))
        }
        // Released when call() returns, i.e. once the headers arrive, before the body is read.
        return semaphore.withPermit { call() }
    }

    /** The host [url] shares with [baseUrl], or null when they differ or either is unparsable. */
    private fun sharedHost(baseUrl: String?, url: String?): String? {
        val imageHost = url?.toHttpUrlOrNull()?.host ?: return null
        val apiHost = baseUrl?.toHttpUrlOrNull()?.host ?: return null
        return imageHost.takeIf { it == apiHost }
    }
}
// KMK <--
