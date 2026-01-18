package eu.kanade.tachiyomi.network

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global request limiter used to cap concurrent network requests.
 *
 * This replaces extension-specific rate limits with a shared concurrency cap
 * to improve throughput while avoiding server overload.
 */
object GlobalRequestLimiter {

    private const val DEFAULT_MAX_CONCURRENT_REQUESTS = 8

    private val maxPermits = AtomicInteger(DEFAULT_MAX_CONCURRENT_REQUESTS)

    @Volatile
    private var semaphore = Semaphore(DEFAULT_MAX_CONCURRENT_REQUESTS, true)

    /**
     * Configure the maximum number of concurrent requests.
     *
     * Intended to be called once at app startup if customization is needed.
     */
    @Synchronized
    fun configure(maxConcurrentRequests: Int) {
        require(maxConcurrentRequests > 0) {
            "maxConcurrentRequests must be greater than 0"
        }

        maxPermits.set(maxConcurrentRequests)
        semaphore = Semaphore(maxConcurrentRequests, true)
    }

    /**
     * Acquire a permit.
     *
     * This call is interruptible to allow proper cancellation and
     * avoid blocking shutdowns or cancelled operations.
     */
    @Throws(InterruptedException::class)
    fun acquire() {
        semaphore.acquire()
    }

    /**
     * Release a previously acquired permit.
     */
    fun release() {
        semaphore.release()
    }

    /**
     * Scoped helper that guarantees acquire/release pairing.
     *
     * Prefer this over manual acquire()/release() calls to avoid
     * leaked permits and deadlocks.
     */
    inline fun <T> withPermit(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }
}
