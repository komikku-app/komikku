package eu.kanade.tachiyomi.util.upscale

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sostituisce un Semaphore(1) semplice quando serve dare priorità a certe
 * richieste (pagina visibile) rispetto ad altre (prefetch in background),
 * senza però poter interrompere un'inferenza già in corso: la priorità
 * incide solo su chi viene servito per primo tra le richieste IN ATTESA,
 * non su quella eventualmente già in esecuzione.
 */
object UpscalePriorityGate {

    enum class Priority { VISIBLE, PREFETCH }

    private val guard = Mutex() // protegge solo la contabilità sotto, mai il lavoro vero
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
        waiter.await() // sospende qui, senza polling, finché non tocca il turno

        try {
            return block()
        } finally {
            guard.withLock {
                val next = highPriorityQueue.removeFirstOrNull() ?: lowPriorityQueue.removeFirstOrNull()
                if (next != null) {
                    next.complete(Unit) // passa lo slot direttamente al prossimo, senza rilascio "a vuoto"
                } else {
                    slotFree = true
                }
            }
        }
    }
}
