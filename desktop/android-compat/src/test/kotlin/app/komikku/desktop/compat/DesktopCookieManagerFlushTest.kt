package app.komikku.desktop.compat

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DesktopCookieManagerFlushTest {

    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("cookies.json")

    private fun cookieCount(text: String) = Json.parseToJsonElement(text).jsonObject.getValue("cookies").jsonArray.size

    @Test
    fun `flushes from several threads never interleave and leave the last state on disk`() {
        val manager = DesktopCookieManager(file)
        val threads = 6
        val rounds = 60
        val start = CountDownLatch(1)
        val done = AtomicBoolean(false)
        val unreadable = AtomicReference<Throwable?>(null)
        val reads = AtomicInteger()
        val reader = thread(name = "cookie-reader") {
            while (!done.get()) {
                if (!file.isFile) continue
                try {
                    cookieCount(file.readText())
                    reads.incrementAndGet()
                } catch (e: Exception) {
                    if (e !is java.io.IOException) unreadable.compareAndSet(null, e)
                }
            }
        }
        val writers = (0 until threads).map { t ->
            thread(name = "cookie-writer-$t") {
                start.await()
                repeat(rounds) { round ->
                    manager.setCookie("https://example.com/", "shared=$t-$round; Max-Age=3600")
                    manager.setCookie("https://example.com/", "own$t=$round; Max-Age=3600")
                    manager.flush()
                }
            }
        }
        start.countDown()
        writers.forEach { it.join() }
        done.set(true)
        reader.join()

        unreadable.get() shouldBe null
        reads.get() shouldNotBe 0
        cookieCount(file.readText()) shouldBe threads + 1
        DesktopCookieManager(file).getCookie("https://example.com/") shouldBe manager.getCookie("https://example.com/")
        dir.listFiles()!!.map { it.name }.filter { it != "cookies.json" }.shouldBeEmpty()
    }

    @Test
    fun `a file held by another process is retried, then the old file is kept`() {
        val failuresLeft = AtomicInteger(2)
        val retried = DesktopCookieManager(file, System::currentTimeMillis) { from, to ->
            if (failuresLeft.getAndDecrement() > 0) throw FileSystemException("$from -> $to: being used by another process")
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
        retried.setCookie("https://example.com/", "a=1; Max-Age=3600")
        retried.flush()
        DesktopCookieManager(file).getCookie("https://example.com/") shouldBe "a=1"

        val locked = DesktopCookieManager(file, System::currentTimeMillis) { from: Path, to: Path ->
            throw FileSystemException("$from -> $to: being used by another process")
        }
        locked.setCookie("https://example.com/", "a=2; Max-Age=3600")
        locked.flush()

        DesktopCookieManager(file).getCookie("https://example.com/") shouldBe "a=1"
        dir.listFiles()!!.map { it.name } shouldBe listOf("cookies.json")
    }
}
