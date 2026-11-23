package app.komikku.quickjs

import app.cash.quickjs.QuickJs
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.dokar.quickjs.QuickJs as Engine

/**
 * quickjs-kt engines are created and closed under the monitor of `com.dokar.quickjs.QuickJs`, which `:source-js`
 * shares (concurrent creates / closes crashed the JVM: PROGRESS.md "Review fixes (I2b)").
 */
class EngineLifecycleTest {

    @Test
    fun `creating and closing wait for the process-wide quickjs-kt lock`() {
        val created = CompletableFuture<QuickJs>()
        synchronized(Engine::class.java) {
            Thread { created.complete(QuickJs.create()) }.apply { isDaemon = true }.start()
            assertThrows<TimeoutException> { created.get(500, TimeUnit.MILLISECONDS) }
        }
        val js = created.get(10, TimeUnit.SECONDS)
        js.evaluate("1 + 1") shouldBe 2
        val closed = CompletableFuture<Unit>()
        synchronized(Engine::class.java) {
            Thread { closed.complete(js.close()) }.apply { isDaemon = true }.start()
            assertThrows<TimeoutException> { closed.get(500, TimeUnit.MILLISECONDS) }
        }
        closed.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `engines created and closed on many threads at once keep working`() {
        val threads = List(8) {
            Thread {
                repeat(200) {
                    QuickJs.create().use { js -> (js.evaluate("[1, 2.5, 'x']") as Array<*>).toList() shouldBe listOf(1, 2.5, "x") }
                }
            }
        }
        val failures = mutableListOf<Throwable>()
        threads.forEach { thread ->
            thread.setUncaughtExceptionHandler { _, e -> synchronized(failures) { failures += e } }
            thread.start()
        }
        threads.forEach { it.join() }
        failures shouldBe emptyList()
    }
}
