package app.komikku.sourcejs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * `runBlocking` with a time limit: a test that hangs on the simulator fails with its name instead of stalling the CI
 * job until its own timeout.
 */
internal fun timedTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = 30_000,
    block: suspend CoroutineScope.() -> Unit,
) = runBlocking(context) {
    withTimeout(timeoutMillis) { block() }
}
