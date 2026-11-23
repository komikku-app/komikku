package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngineLimits
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** The out-of-memory scenarios of [OutOfMemoryCrashTest], by name. */
private val scenarios = mapOf(
    "push" to """async getPopular(page) { const a = []; while (true) a.push("x".repeat(1000) + a.length); }""",
    "object" to """async getPopular(page) { const o = {}; let i = 0; while (true) o["k" + i] = [i++]; }""",
    "filters" to """getFilterList() { const a = []; while (true) a.push("x".repeat(1000) + a.length); } async search(q, p, f) { return { list: [] }; }""",
    "headers" to """getHeaders(url) { const a = []; while (true) a.push({ i: a.length, s: "y".repeat(500) }); }""",
)

/** Child JVM: runs one scenario under a heap limit (MiB) and prints its outcome. */
fun main(args: Array<String>) = runBlocking {
    val limits = JsHostLimits(callTimeoutMillis = 60_000, engine = JsEngineLimits(memoryLimitBytes = args[1].toLong() * 1024 * 1024))
    val extension = TestHost(limits = limits).extension(provider(scenarios.getValue(args[0])))
    val outcome = runCatching {
        when (args[0]) {
            "filters" -> extension.search("q", 1)
            "headers" -> extension.metadata()
            else -> extension.getPopular(1)
        }
    }
    println("RESULT ${outcome.exceptionOrNull()?.let { it::class.simpleName } ?: "returned"}")
}

/**
 * QuickJS (2026-06-04, through quickjs-kt 1.0.15) crashed the JVM (SIGSEGV in `JS_DefineProperty`) when a source ran
 * out of memory, depending only on the shape of the prelude's code (plain instead of async `Client` methods, one
 * helper function extracted): PROGRESS.md "Review fixes (I2b)". Each scenario runs in a child JVM, so a crash fails
 * this test with its name instead of aborting the test run.
 */
class OutOfMemoryCrashTest {

    @Test
    fun runningOutOfMemoryNeverCrashesTheJvm() {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val classPath = System.getProperty("java.class.path")
        val crashes = mutableListOf<String>()
        for (scenario in scenarios.keys) {
            for (mib in listOf(8, 16, 32, 64)) {
                val process = ProcessBuilder(java, "-Xmx256m", "-cp", classPath, "app.komikku.sourcejs.OutOfMemoryCrashTestKt", scenario, "$mib")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val finished = process.waitFor(120, TimeUnit.SECONDS)
                val result = output.lineSequence().firstOrNull { it.startsWith("RESULT ") }
                if (!finished || process.exitValue() != 0 || result == null) {
                    process.destroyForcibly()
                    crashes += "$scenario at $mib MiB: exit ${if (finished) process.exitValue() else "timeout"}"
                }
            }
        }
        File(".").listFiles { file -> file.name.startsWith("hs_err_pid") }?.forEach { it.delete() }
        assertEquals(emptyList(), crashes)
    }
}
