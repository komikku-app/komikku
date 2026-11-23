package app.komikku.quickjs

import app.cash.quickjs.QuickJsException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Idle engine threads end and the runtime carries on on a new one (docs/kmp-desktop/PROGRESS.md "Review fixes (I2b)"). */
class EngineThreadTest {

    private fun awaitDeath(thread: Thread) {
        thread.join(10_000)
        thread.isAlive shouldBe false
    }

    @Test
    fun `an idle thread ends and the next call starts a new one`() {
        val engineThread = EngineThread(idleMillis = 100)
        val first = engineThread.run { Thread.currentThread() }
        engineThread.run { engineThread.isCurrent } shouldBe true
        awaitDeath(first)
        engineThread.isCurrent shouldBe false
        val second = engineThread.run { Thread.currentThread() }
        second shouldNotBe first
        engineThread.run { engineThread.isCurrent } shouldBe true
        engineThread.runLastAndShutdown {}
    }

    @Test
    fun `a runtime keeps its state and stack limit on the next thread`() {
        val runtime = QuickJsRuntime.create(idleMillis = 100)
        try {
            val first = runtimeThread(runtime)
            runtime.evaluate("var kept = 41; function f(n) { return f(n + 1) + 1 }", "a.js").unwrap()
            awaitDeath(first)
            runtime.evaluate("kept + 1", "b.js").unwrap() shouldBe 42
            runtimeThread(runtime) shouldNotBe first
            // The JavaScript stack limit applies on the new thread too: a RangeError, not a native overflow.
            assertThrows<QuickJsException> { runtime.evaluate("f(0)", "c.js").unwrap() }.message.orEmpty() shouldContain "stack"
            runtime.evaluate("function g(n) { return n === 0 ? 0 : g(n - 1) + 1 } g(900)", "d.js").unwrap() shouldBe 900
        } finally {
            runtime.close()
        }
    }

    /** The engine thread, seen from a `set` binding. */
    private fun runtimeThread(runtime: QuickJsRuntime): Thread {
        var seen: Thread? = null
        val name = "probe" + System.nanoTime()
        val spec = MethodSpec(Runnable::class.java.getMethod("run"), boxed = false)
        runtime.set(name, Runnable { seen = Thread.currentThread() }, listOf(spec)).unwrap()
        runtime.evaluate("$name.run()", "t.js").unwrap()
        return seen!!
    }
}
