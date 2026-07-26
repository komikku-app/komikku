package eu.kanade.tachiyomi.data.coil

import kotlinx.coroutines.sync.Semaphore
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// KMK -->
/** Limits concurrent image requests for each source. */
internal object SourceImageCallLimiter {

    /** Leaves OkHttp host slots for user requests. */
    private const val CONCURRENCY = 2

    private val semaphores = ConcurrentHashMap<Long, Semaphore>()

    /** Acquires a permit whose ownership follows the guarded response body. */
    suspend fun acquire(sourceId: Long): Permit {
        // `computeIfAbsent` atomically creates the source semaphore.
        val semaphore = semaphores.computeIfAbsent(sourceId) { Semaphore(CONCURRENCY) }
        semaphore.acquire()
        return Permit(semaphore)
    }

    /** Releasing is idempotent, so each exit path can release without tracking the others. */
    class Permit(private val semaphore: Semaphore) {

        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                semaphore.release()
            }
        }

        /**
         * Returns [source] with this permit's release attached to whichever comes first,
         * the source being drained or closed.
         *
         * Close alone is not enough. Coil's file-backed decoding drains the source into a
         * temp file and keeps the reference without closing it, so a permit waiting only on
         * close is held through decoding, and leaks outright if the source is then dropped.
         * Reaching the end of the body means the transfer this permit guards is over.
         */
        fun releaseWhenConsumed(source: BufferedSource): BufferedSource {
            return object : ForwardingSource(source) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    if (bytesRead == -1L) {
                        release()
                    }
                    return bytesRead
                }

                override fun close() {
                    try {
                        super.close()
                    } finally {
                        release()
                    }
                }
            }.buffer()
        }
    }
}
// KMK <--
