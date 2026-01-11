package eu.kanade.tachiyomi.network.interceptor

import android.os.SystemClock
import eu.kanade.tachiyomi.network.NetworkPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(
    RateLimitInterceptor(
        host = null,
        permits = permits,
        period = period.toDuration(unit.toDurationUnit())
    )
)

fun OkHttpClient.Builder.rateLimit(permits: Int, period: Duration = 1.seconds) =
    addInterceptor(RateLimitInterceptor(null, permits, period))

internal class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int,
    private val period: Duration,
) : Interceptor {

    private val timestamps = ArrayDeque<Long>(permits)
    private val rateLimitMillis = period.inWholeMilliseconds
    private val lock = ReentrantLock(true)
    private val condition = lock.newCondition()

    private val preferences: NetworkPreferences by lazy { Injekt.get() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")

        val request = chain.request()

        // Skip rate limits if user has disabled them in preferences
        if (preferences.ignoreRateLimits().get()) {
            return chain.proceed(request)
        }

        // Apply rate limiting only if host matches (or host is null)
        if (host != null && request.url.host != host) {
            return chain.proceed(request)
        }

        val timestamp = SystemClock.elapsedRealtime()

        lock.withLock {
            while (true) {
                cleanExpired()
                if (timestamps.size < permits) {
                    timestamps.addLast(timestamp)
                    break
                }

                val waitTime = timestamps.first + rateLimitMillis - SystemClock.elapsedRealtime()
                if (waitTime > 0) {
                    try {
                        condition.awaitNanos(waitTime * 1_000_000)
                    } catch (e: InterruptedException) {
                        throw IOException("Rate limiter interrupted", e)
                    }
                } else {
                    cleanExpired()
                }

                if (call.isCanceled()) throw IOException("Canceled")
            }
        }

        val response = chain.proceed(request)

        // If response is cached (not a network call), remove it early
        if (response.networkResponse == null) {
            lock.withLock {
                timestamps.removeFirstOccurrence(timestamp)
                condition.signal()
            }
        }

        return response
    }

    private fun cleanExpired() {
        val cutoff = SystemClock.elapsedRealtime() - rateLimitMillis
        while (timestamps.isNotEmpty() && timestamps.first < cutoff) {
            timestamps.removeFirst()
        }
    }
}
