package app.komikku.desktop.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * M2 review fix: one file dialog at a time (macOS's folder switch is a global system property set while the folder
 * panel is open, so a file dialog opened meanwhile would pick folders). A second one is refused as cancelled.
 */
class FileDialogGuardTest {

    @Test
    fun `a dialog asked for while another is showing is refused`() {
        val guard = FileDialogGuard()
        var opened = 0
        val result = guard.exclusive {
            opened++
            guard.isShowing shouldBe true
            // Nested (a modal dialog's event loop running another request on the event thread)
            guard.exclusive {
                opened++
                "second"
            } shouldBe null
            "first"
        }
        result shouldBe "first"
        opened shouldBe 1
        guard.isShowing shouldBe false
        // Once closed, the next one opens
        guard.exclusive { "next" } shouldBe "next"
    }

    @Test
    fun `a dialog from another thread is refused too`() {
        val guard = FileDialogGuard()
        val showing = CountDownLatch(1)
        val close = CountDownLatch(1)
        val first = thread {
            guard.exclusive {
                showing.countDown()
                close.await(5, TimeUnit.SECONDS)
                "first"
            }
        }
        showing.await(5, TimeUnit.SECONDS) shouldBe true
        guard.exclusive { "second" } shouldBe null
        close.countDown()
        first.join(5_000)
        guard.exclusive { "third" } shouldBe "third"
    }

    @Test
    fun `the guard is released when a dialog fails`() {
        val guard = FileDialogGuard()
        shouldThrow<IllegalStateException> { guard.exclusive<String> { error("dialog failed") } }
        guard.isShowing shouldBe false
        guard.exclusive { "after" } shouldBe "after"
    }
}
