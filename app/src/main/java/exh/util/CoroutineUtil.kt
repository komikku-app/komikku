package exh.util

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.coroutineContext

fun <T> Flow<T>.cancellable() = onEach {
    coroutineContext.ensureActive()
}
