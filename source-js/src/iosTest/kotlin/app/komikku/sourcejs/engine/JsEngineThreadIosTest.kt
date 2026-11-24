package app.komikku.sourcejs.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS engine thread has the stack the JS limit needs (docs/kmp-desktop/IOS_CI.md). */
class JsEngineThreadIosTest {

    @Test
    fun theEngineThreadHoldsTheFullJsStack() = runBlocking {
        val limits = JsEngineLimits()
        val thread = newJsEngineThread("test-js", limits.maxStackSizeBytes)
        try {
            assertEquals(limits.maxStackSizeBytes, thread.maxJsStackBytes)
            val dispatcher = thread.dispatcher as NSThreadDispatcher
            assertTrue(dispatcher.stackSize > limits.maxStackSizeBytes, "stack ${dispatcher.stackSize}")
            val (name, stackSize) = withContext(thread.dispatcher) {
                NSThread.currentThread.name to NSThread.currentThread.stackSize.toLong()
            }
            assertEquals("test-js", name)
            assertEquals(dispatcher.stackSize, stackSize)
        } finally {
            thread.dispatcher.close()
        }
    }
}
