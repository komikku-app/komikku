package app.cash.quickjs

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The finalizer of an instance that was never closed must not close it while it is still in use
 * (docs/kmp-desktop/PROGRESS.md "Review fixes (I2b)"; quickjs-android's proxies kept the instance alive).
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class QuickJsFinalizerTest {

    interface Calc {
        fun add(a: Int, b: Double): Double
    }

    fun interface Blocker {
        fun block(): Int
    }

    @Suppress("DEPRECATION")
    private fun collect(weak: WeakReference<*>) {
        repeat(30) {
            System.gc()
            System.runFinalization()
            if (weak.get() == null) return
            Thread.sleep(20)
        }
    }

    /** A proxy whose instance nobody else references. */
    private fun orphanProxy(): Pair<Calc, WeakReference<QuickJs>> {
        val js = QuickJs.create()
        js.evaluate("var calc = { add: function (a, b) { return a + b; } };")
        return js.get("calc", Calc::class.java) to WeakReference(js)
    }

    @Test
    fun `a get proxy keeps its instance alive`() {
        val (calc, weak) = orphanProxy()
        collect(weak)
        weak.get().shouldNotBeNull()
        calc.add(1, 2.5) shouldBe 3.5
        weak.get()?.close()
    }

    @Test
    fun `an instance is not finalized while its call is in flight`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val weak = startBlockedCall(entered, release)
        val result = weak.second
        entered.await(10, TimeUnit.SECONDS) shouldBe true
        collect(weak.first)
        weak.first.get().shouldNotBeNull()
        release.countDown()
        result.get(20, TimeUnit.SECONDS) shouldBe 7
    }

    /** Starts `host.block()` on another thread that only holds the instance during the call itself. */
    private fun startBlockedCall(entered: CountDownLatch, release: CountDownLatch): Pair<WeakReference<QuickJs>, CompletableFuture<Any?>> {
        val js = QuickJs.create()
        js.set(
            "host",
            Blocker::class.java,
            Blocker {
                entered.countDown()
                release.await()
                7
            },
        )
        val weak = WeakReference(js)
        val result = CompletableFuture<Any?>()
        Thread {
            result.complete(runCatching { weak.get()?.evaluate("host.block()") }.getOrElse { it })
        }.apply { isDaemon = true }.start()
        return weak to result
    }
}
