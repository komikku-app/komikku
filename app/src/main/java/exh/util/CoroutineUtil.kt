package exh.util

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext

fun <T> Flow<T>.cancellable() = onEach {
    coroutineContext.ensureActive()
}

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> maybeRunBlocking(runBlocking: Boolean, crossinline block: suspend () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return if (runBlocking) {
        runBlocking { block() }
    } else {
        block()
    }
}
