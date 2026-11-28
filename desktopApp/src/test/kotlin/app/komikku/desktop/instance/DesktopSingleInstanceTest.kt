package app.komikku.desktop.instance

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

/**
 * [DesktopSingleInstance]: one running instance per data folder. Second starts run in a child JVM where it matters (the
 * lock is per process): they exit with 0 and the running instance's "show window" callback runs; a killed holder's lock
 * is released by the system and its port file ignored; two data folders do not affect each other; a wrong token or
 * garbage is dropped without an answer; no answer within the timeout means "busy" (exit 1). An instance that is shutting
 * down no longer answers.
 */
class DesktopSingleInstanceTest {

    @TempDir
    lateinit var tempDir: File

    private val instances = mutableListOf<DesktopSingleInstance>()
    private val children = mutableListOf<Child>()

    @AfterEach
    fun tearDown() {
        children.forEach { it.kill() }
        instances.forEach { it.close() }
    }

    @Test
    fun `a second start exits with code 0 and the running instance shows its window`() {
        val dataDir = folder("data")
        val shown = Semaphore(0)
        primary(dataDir).onShowRequest = { shown.release() }

        val second = startChild(dataDir)

        second.process.waitFor(CHILD_TIMEOUT_S, TimeUnit.SECONDS) shouldBe true
        second.process.exitValue() shouldBe 0
        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
        second.remainingOutput() shouldBe emptyList()
    }

    @Test
    fun `a second start in the same process is told the folder is taken and signals the first`() {
        val dataDir = folder("data")
        val shown = Semaphore(0)
        primary(dataDir).onShowRequest = { shown.release() }

        DesktopSingleInstance.acquire(dataDir) shouldBe DesktopSingleInstance.Outcome.ShownOther

        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
    }

    @Test
    fun `a show request that comes before the window is there is delivered when it is`() {
        val dataDir = folder("data")
        val instance = primary(dataDir)

        DesktopSingleInstance.acquire(dataDir) shouldBe DesktopSingleInstance.Outcome.ShownOther
        val shown = Semaphore(0)
        instance.onShowRequest = { shown.release() }

        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
    }

    @Test
    fun `the lock of a killed process is released and its stale port file is ignored`() {
        val dataDir = folder("data")
        val portFile = dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)
        val holder = startChild(dataDir)
        holder.awaitLine("LOCKED")

        DesktopSingleInstance.acquire(dataDir) shouldBe DesktopSingleInstance.Outcome.ShownOther
        holder.awaitLine("SHOW")
        val stale = InstancePortFile.read(portFile).shouldNotBeNull()

        holder.kill()
        InstancePortFile.read(portFile) shouldBe stale

        val shown = Semaphore(0)
        primary(dataDir).onShowRequest = { shown.release() }
        val current = InstancePortFile.read(portFile).shouldNotBeNull()
        current shouldNotBe stale
        current.token shouldNotBe stale.token
        DesktopSingleInstance.acquire(dataDir) shouldBe DesktopSingleInstance.Outcome.ShownOther
        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
    }

    @Test
    fun `two data folders run side by side`() {
        val first = folder("first")
        val second = folder("second")
        val other = startChild(second)
        other.awaitLine("LOCKED")

        val shown = Semaphore(0)
        primary(first).onShowRequest = { shown.release() }

        other.process.isAlive shouldBe true
        DesktopSingleInstance.acquire(second) shouldBe DesktopSingleInstance.Outcome.ShownOther
        other.awaitLine("SHOW")
        shown.availablePermits() shouldBe 0
        DesktopSingleInstance.acquire(first) shouldBe DesktopSingleInstance.Outcome.ShownOther
        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
    }

    @Test
    fun `a wrong token or a garbage request is ignored and the connection closed`() {
        val dataDir = folder("data")
        val shown = Semaphore(0)
        primary(dataDir, readTimeoutMs = 300).onShowRequest = { shown.release() }
        val endpoint = InstancePortFile.read(dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)).shouldNotBeNull()
        val wrongToken = InstanceSignal.newToken()

        send(endpoint.port, InstanceSignal.request(wrongToken)) shouldBe null
        send(endpoint.port, "SHOW ${endpoint.token} now\n".toByteArray()) shouldBe null
        send(endpoint.port, "QUIT ${endpoint.token}\n".toByteArray()) shouldBe null
        send(endpoint.port, "SHOW ${endpoint.token}".toByteArray()) shouldBe null
        send(endpoint.port, ByteArray(64 * 1024) { 'A'.code.toByte() }) shouldBe null
        val start = System.nanoTime()
        send(endpoint.port, ByteArray(0), endRequest = false) shouldBe null
        (System.nanoTime() - start) / 1_000_000 shouldBeLessThan 3_000L
        shown.availablePermits() shouldBe 0

        send(endpoint.port, InstanceSignal.request(endpoint.token)) shouldBe "OK\n"
        shown.tryAcquire(5, TimeUnit.SECONDS) shouldBe true
        shown.availablePermits() shouldBe 0
    }

    @Test
    fun `a running instance that does not answer within two seconds makes the start report the folder as busy`() {
        val dataDir = folder("data")
        primary(dataDir)
        val portFile = dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)
        ServerSocket(0, 1, InstanceSignal.loopback).use { silent ->
            InstancePortFile.write(portFile, InstancePortFile.Endpoint(silent.localPort, InstanceSignal.newToken()))
            var busy: File? = null

            val start = System.nanoTime()
            val exit = shouldThrow<Exit> {
                DesktopSingleInstance.ensure(dataDir, exit = { throw Exit(it) }, showBusy = { busy = it })
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            exit.code shouldBe 1
            busy shouldBe dataDir
            elapsedMs shouldBeGreaterThanOrEqual DesktopSingleInstance.ANSWER_TIMEOUT.inWholeMilliseconds
            elapsedMs shouldBeLessThan DesktopSingleInstance.ANSWER_TIMEOUT.inWholeMilliseconds + 2_000
        }

        Files.write(portFile, "garbage\n".toByteArray())
        DesktopSingleInstance.acquire(dataDir, answerTimeout = 300.milliseconds) shouldBe
            DesktopSingleInstance.Outcome.OtherNotAnswering
        stringResource(KMR.strings.desktop_single_instance_busy, dataDir.path) shouldContain "\n${dataDir.path}\n"
    }

    @Test
    fun `the port file is readable by its owner only`() {
        val dataDir = folder("data")
        primary(dataDir)
        val portFile = dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)
        assumeTrue("posix" in portFile.fileSystem.supportedFileAttributeViews())

        PosixFilePermissions.toString(Files.getPosixFilePermissions(portFile)) shouldBe "rw-------"
    }

    @Test
    fun `a start while the running instance shuts down is not told shown and takes the folder once it is free`() {
        val dataDir = folder("data")
        val quitting = primary(dataDir)
        quitting.onShowRequest = { error("a quitting instance must not be asked to show its window") }

        quitting.stopListening()
        Files.exists(dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)) shouldBe false
        DesktopSingleInstance.acquire(dataDir, answerTimeout = 300.milliseconds) shouldBe
            DesktopSingleInstance.Outcome.OtherNotAnswering
        val exited = thread {
            Thread.sleep(500)
            quitting.close()
        }

        val outcome = DesktopSingleInstance.acquire(dataDir)

        exited.join()
        instances -= quitting
        instances += outcome.shouldBeInstanceOf<DesktopSingleInstance.Outcome.Primary>().instance
    }

    @Test
    fun `closing the instance frees the folder and removes the port file`() {
        val dataDir = folder("data")
        val instance = primary(dataDir)

        instance.close()
        instances -= instance

        Files.exists(dataDir.toPath().resolve(DesktopSingleInstance.PORT_FILE)) shouldBe false
        primary(dataDir)
    }

    private class Exit(val code: Int) : RuntimeException()

    private fun folder(name: String): File = File(tempDir, name).apply { mkdirs() }

    private fun primary(dataDir: File, readTimeoutMs: Long = 1_000): DesktopSingleInstance {
        val outcome = DesktopSingleInstance.acquire(dataDir, requestReadTimeout = readTimeoutMs.milliseconds)
        return outcome.shouldBeInstanceOf<DesktopSingleInstance.Outcome.Primary>().instance.also { instances += it }
    }

    /**
     * Sends [request] (then ends the stream when [endRequest]; otherwise the server has to time out) and returns the
     * answer, or `null` when the connection was closed without one.
     */
    private fun send(port: Int, request: ByteArray, endRequest: Boolean = true): String? = Socket().use { socket ->
        socket.connect(InetSocketAddress(InstanceSignal.loopback, port), 2_000)
        socket.soTimeout = 5_000
        try {
            socket.getOutputStream().apply {
                write(request)
                flush()
            }
            if (endRequest) socket.shutdownOutput()
        } catch (_: IOException) {
            // The server may close a too-long request before it was all sent
        }
        val answer = try {
            socket.getInputStream().readAllBytes()
        } catch (_: IOException) {
            ByteArray(0)
        }
        answer.takeIf { it.isNotEmpty() }?.toString(Charsets.US_ASCII)
    }

    private class Child(val process: Process) {
        private val queue = LinkedBlockingQueue<String>()
        private val lines = mutableListOf<String>()
        private val reader = thread(isDaemon = true, name = "single instance child output") {
            process.inputStream.bufferedReader().forEachLine { queue.put(it) }
        }

        /** What an exited child printed that was not awaited yet. */
        fun remainingOutput(): List<String> {
            reader.join(TimeUnit.SECONDS.toMillis(CHILD_TIMEOUT_S))
            return buildList { queue.drainTo(this) }
        }

        fun awaitLine(expected: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CHILD_TIMEOUT_S)
            while (true) {
                val line = queue.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
                    ?: error("The child did not print $expected (printed $lines)")
                lines += line
                if (line == expected) return
            }
        }

        fun kill() {
            process.destroyForcibly()
            process.waitFor(CHILD_TIMEOUT_S, TimeUnit.SECONDS)
        }
    }

    /** Starts [SingleInstanceChild] on [dataDir] in a new JVM with this test's class path (through an argument file). */
    private fun startChild(dataDir: File): Child {
        val index = children.size
        val argFile = File(tempDir, "child-$index.args")
        val classPath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"")
        argFile.writeText("-cp\n\"$classPath\"\n")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java,
            "-Djava.awt.headless=true",
            "@${argFile.path}",
            SingleInstanceChild::class.java.name,
            dataDir.path,
        )
            .redirectError(File(tempDir, "child-$index.log"))
            .start()
        return Child(process).also { children += it }
    }

    private companion object {
        const val CHILD_TIMEOUT_S = 30L
    }
}
