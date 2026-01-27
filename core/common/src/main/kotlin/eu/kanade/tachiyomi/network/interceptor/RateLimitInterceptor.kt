package eu.kanade.tachiyomi.network.interceptor

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration

/**
 * Reactive network safety interceptor with emergency brake.
 * * Features:
 * - Unlimited speed by default (0ms delay).
 * - Automatic backoff only when the server signals overload (429/503).
 * - Conservative recovery to prevent "flapping" (fast -> ban -> fast).
 * - Emergency pause after 3 consecutive errors to prevent permanent IP bans.
 * - Thread-safe and per-host isolated.
 * - Optimized for battery: immediately exits if the user cancels the request.
 */
internal class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int, // kept for compatibility
    period: Duration,         // kept for compatibility
) : Interceptor {

    private data class State(
        var penaltyUntil: Long = 0L,
        var backoffMillis: Long = BASE_BACKOFF,
        var consecutive429: Int = 0
    )

    private val states = ConcurrentHashMap<String, State>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestHost = request.url.host

        if (host != null && requestHost != host) {
            return chain.proceed(request)
        }

        val state = states.getOrPut(requestHost) { State() }

        val waitTime = synchronized(state) {
            val now = SystemClock.elapsedRealtime()
            if (now < state.penaltyUntil) state.penaltyUntil - now else 0L
        }

        try {
            if (waitTime > 0) {
                // OPTIMIZATION: Check if user cancelled before starting a long sleep
                if (chain.call().isCanceled()) throw IOException("Canceled")
                Thread.sleep(waitTime)
            } else {
                Thread.sleep(Random.nextLong(5, 20))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted during network backoff")
        }

        // Double check after waking up, just in case cancellation happened during sleep
        if (chain.call().isCanceled()) throw IOException("Canceled")

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            registerFailure(state)
            throw e
        }

        synchronized(state) {
            when (response.code) {
                429, 503 -> applyServerBackoff(state, response)
                in 200..399 -> recover(state)
            }
        }

        return response
    }

    private fun applyServerBackoff(state: State, response: Response) {
        state.consecutive429++

        if (state.consecutive429 >= MAX_429_BEFORE_BRAKE) {
            state.penaltyUntil = SystemClock.elapsedRealtime() + EMERGENCY_PAUSE
            state.backoffMillis = BASE_BACKOFF
            state.consecutive429 = 0
            return
        }

        val retryAfter = response.header("Retry-After")
            ?.toLongOrNull()
            ?.times(1000)

        val delay = retryAfter ?: state.backoffMillis + jitter()
        state.penaltyUntil = SystemClock.elapsedRealtime() + delay
        state.backoffMillis = min(state.backoffMillis * 2, MAX_BACKOFF)
    }

    private fun recover(state: State) {
        state.consecutive429 = 0
        state.backoffMillis = maxOf(BASE_BACKOFF, state.backoffMillis / 4)
    }

    private fun registerFailure(state: State) {
        synchronized(state) {
            state.penaltyUntil = SystemClock.elapsedRealtime() + state.backoffMillis
            state.backoffMillis = min(state.backoffMillis * 2, MAX_BACKOFF)
        }
    }

    private fun jitter(): Long {
        return Random.nextLong(5, 10) + Random.nextLong(0, 10)
    }

    private companion object {
        private const val BASE_BACKOFF = 500L
        private const val MAX_BACKOFF = 15_000L

        private const val MAX_429_BEFORE_BRAKE = 3
        private const val EMERGENCY_PAUSE = 60_000L
    }
}
