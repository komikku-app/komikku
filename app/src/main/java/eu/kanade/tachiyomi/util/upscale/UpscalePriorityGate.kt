package eu.kanade.tachiyomi.util.upscale

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Replaces a simple Semaphore(1) when you need to give priority to certain
 * requests (visible page) versus others (background prefetch),
 * without however being able to interrupt an ongoing inference: the priority
 * only affects who is served first among the WAITING requests,
 * not on the one that is possibly already running.
 */
object UpscalePriorityGate {

    enum class Priority { VISIBLE, PREFETCH }

    private val guard = Mutex()
    private var slotFree = true
    private val highPriorityQueue = ArrayDeque<CompletableDeferred<Unit>>()
    private val lowPriorityQueue = ArrayDeque<CompletableDeferred<Unit>>()

    suspend fun <T> withPermit(priority: Priority, block: suspend () -> T): T {
        val waiter = CompletableDeferred<Unit>()
        guard.withLock {
            if (slotFree) {
                slotFree = false
                waiter.complete(Unit)
            } else if (priority == Priority.VISIBLE) {
                highPriorityQueue.addLast(waiter)
            } else {
                lowPriorityQueue.addLast(waiter)
            }
        }
        waiter.await() // suspends here, without polling, until it's its turn

        try {
            return block()
        } finally {
            guard.withLock {
                val next = highPriorityQueue.removeFirstOrNull() ?: lowPriorityQueue.removeFirstOrNull()
                if (next != null) {
                    next.complete(Unit)
                } else {
                    slotFree = true
                }
            }
        }
    }
}
