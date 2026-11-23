package app.komikku.sourcejs.engine

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Engines are created and closed under the monitor of `com.dokar.quickjs.QuickJs`, shared with `:core:quickjs`
 * (concurrent creates / closes crashed the JVM: PROGRESS.md "Review fixes (I2b)").
 */
class QuickJsLifecycleTest {

    @Test
    fun creatingAndClosingWaitForTheProcessWideQuickJsKtLock() = runBlocking {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            synchronized(QuickJs::class.java) {
                held.countDown()
                release.await()
            }
        }.apply {
            isDaemon = true
            start()
        }
        held.await()
        val creating = async(Dispatchers.Default) { QuickJsEngine.create(JsEngineLimits()) }
        assertNull(withTimeoutOrNull(500) { creating.await() })
        release.countDown()
        holder.join()
        val engine = withTimeout(10_000) { creating.await() }
        engine.evaluate("1", "x.js")

        val held2 = CountDownLatch(1)
        val release2 = CountDownLatch(1)
        Thread {
            synchronized(QuickJs::class.java) {
                held2.countDown()
                release2.await()
            }
        }.apply {
            isDaemon = true
            start()
        }
        held2.await()
        val closing = async(Dispatchers.Default) { engine.close() }
        delay(500)
        assertFalse(closing.isCompleted)
        release2.countDown()
        withTimeout(10_000) { closing.await() }
    }
}
