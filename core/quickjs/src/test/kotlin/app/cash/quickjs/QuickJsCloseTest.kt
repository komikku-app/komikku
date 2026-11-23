package app.cash.quickjs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * `close()` from another thread ends a call that never finishes (quickjs-kt keeps running pending jobs and awaits
 * the script's promise; docs/kmp-desktop/PROGRESS.md "Review fixes (I2b)"). Before, `close()` queued behind it and
 * hung too.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class QuickJsCloseTest {

    private fun callOnThread(block: () -> Any?): CompletableFuture<Any?> {
        val result = CompletableFuture<Any?>()
        Thread {
            try {
                result.complete(block())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }.apply { isDaemon = true }.start()
        return result
    }

    private fun assertCloseEnds(script: String) {
        val js = QuickJs.create()
        val stuck = callOnThread { js.evaluate(script) }
        assertThrows<TimeoutException> { stuck.get(300, TimeUnit.MILLISECONDS) }
        val queued = callOnThread { js.evaluate("1") }
        val closing = callOnThread { js.close() }
        closing.get(20, TimeUnit.SECONDS)
        listOf(stuck, queued).forEach { call ->
            val error = assertThrows<java.util.concurrent.ExecutionException> { call.get(20, TimeUnit.SECONDS) }.cause
            error.shouldBeInstanceOf<NullPointerException>()
            error.message shouldBe "Null QuickJs context - did you close your QuickJs?"
        }
        assertThrows<NullPointerException> { js.evaluate("1") }
    }

    @Test
    fun `close ends a script that keeps scheduling promise jobs`() {
        assertCloseEnds("(function f() { Promise.resolve().then(f); })(); 1")
    }

    @Test
    fun `close ends a top-level await that never settles`() {
        assertCloseEnds("await new Promise(function () {})")
    }

    @Test
    fun `close ends a busy loop`() {
        assertCloseEnds("while (true) {}")
    }

    @Test
    fun `an instance that was not busy closes as before`() {
        val js = QuickJs.create()
        js.evaluate("1 + 1") shouldBe 2
        js.close()
        js.close()
        assertThrows<NullPointerException> { js.evaluate("1") }
    }
}
