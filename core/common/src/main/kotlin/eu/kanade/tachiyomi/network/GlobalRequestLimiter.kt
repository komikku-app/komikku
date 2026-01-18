package eu.kanade.tachiyomi.network

import java.util.concurrent.Semaphore

/**
 * Global request limiter used to cap concurrent network requests.
 * This intentionally replaces extension-specific throttling behavior.
 */
object GlobalRequestLimiter {

    // Conservative default to avoid overwhelming servers
    private const val MAX_CONCURRENT_REQUESTS = 8

    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS, true)

    fun acquire() {
        semaphore.acquireUninterruptibly()
    }

    fun release() {
        semaphore.release()
    }
}
