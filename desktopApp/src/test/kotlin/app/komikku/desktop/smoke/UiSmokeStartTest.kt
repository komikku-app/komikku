package app.komikku.desktop.smoke

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Only `--ui-smoke=<report folder>` switches the smoke mode on, it runs alone, and it never starts on a data folder that
 * is not a new temporary one (ANSWER 011, condition 4). The refusals run `main()`'s first call in a child JVM with its
 * own home, temporary and data folders, which are checked untouched afterwards.
 */
class UiSmokeStartTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `a normal start is not a smoke run`() {
        UiSmoke.isRequested(emptyArray()) shouldBe false
        UiSmoke.isRequested(arrayOf("--self-test")) shouldBe false
        UiSmoke.isRequested(arrayOf("--ui-smoke=out")) shouldBe true
        UiSmoke.isRequested(arrayOf(UiSmoke.KEEP_ARGUMENT)) shouldBe true
    }

    @Test
    fun `the report folder is the only argument it needs`() {
        val run = UiSmoke.parse(arrayOf("--ui-smoke=${root.path}", UiSmoke.KEEP_ARGUMENT)).shouldBeInstanceOf<UiSmoke.Request.Run>()

        run.reportFolder shouldBe root.absoluteFile
        run.keepDataFolder shouldBe true
        UiSmoke.parse(arrayOf("--ui-smoke=${root.path}")).shouldBeInstanceOf<UiSmoke.Request.Run>().keepDataFolder shouldBe false
    }

    @Test
    fun `it is refused without a report folder, twice, or with any other argument`() {
        UiSmoke.parse(arrayOf("--ui-smoke")).shouldBeInstanceOf<UiSmoke.Request.Refused>().reason shouldContain "needs a report folder"
        UiSmoke.parse(arrayOf("--ui-smoke=")).shouldBeInstanceOf<UiSmoke.Request.Refused>()
        UiSmoke.parse(arrayOf(UiSmoke.KEEP_ARGUMENT)).shouldBeInstanceOf<UiSmoke.Request.Refused>()
        UiSmoke.parse(arrayOf("--ui-smoke=a", "--ui-smoke=b")).shouldBeInstanceOf<UiSmoke.Request.Refused>()
            .reason shouldContain "more than once"
        for (other in listOf("--self-test", "--self-test=notify", "--self-test-extensions=x", "--anything")) {
            UiSmoke.parse(arrayOf("--ui-smoke=${root.path}", other)).shouldBeInstanceOf<UiSmoke.Request.Refused>()
                .reason shouldContain other
        }
    }

    @Test
    fun `a start with another mode argument exits 2 and reports the refusal`() {
        val report = File(root, "report")
        val child = runChild(args = listOf("--ui-smoke=${report.path}", "--self-test"))

        child.exitCode shouldBe UiSmoke.EXIT_REFUSED
        File(report, SmokeReport.TEXT_FILE).readText().trimEnd() shouldEndWith SmokeReport.Outcome.REFUSED.line
        temporaryFolder.listFiles().orEmpty().toList().shouldBeEmpty()
    }

    @Test
    fun `a temporary folder inside a given data folder is refused and the data folder untouched`() {
        val given = File(root, "given").apply { mkdirs() }
        File(given, "marker").writeText("user data")
        val temp = File(given, "tmp").apply { mkdirs() }
        val report = File(root, "report")

        val child = runChild(args = listOf("--ui-smoke=${report.path}"), dataDir = given, tempDir = temp)

        child.exitCode shouldBe UiSmoke.EXIT_REFUSED
        child.output shouldContain "lies inside the data folder"
        given.walk().map { it.relativeTo(given).path }.toSet() shouldBe setOf("", "marker", "tmp")
        File(given, "marker").readText() shouldBe "user data"
        File(report, SmokeReport.TEXT_FILE).readText() shouldContain "lies inside the data folder"
    }

    @Test
    fun `a temporary folder inside the default data folder is refused and the data folder untouched`() {
        val default = DesktopDirs.resolveDataDir(System.getProperty("os.name"), home.path, null, childEnv()::get)
        default.mkdirs()
        File(default, "marker").writeText("user data")
        val temp = File(default, "tmp").apply { mkdirs() }

        val child = runChild(args = listOf("--ui-smoke=${File(root, "report").path}"), tempDir = temp)

        child.exitCode shouldBe UiSmoke.EXIT_REFUSED
        child.output shouldContain "lies inside the data folder"
        default.walk().map { it.relativeTo(default).path }.toSet() shouldBe setOf("", "marker", "tmp")
    }

    @Test
    fun `a start on a new temporary folder is accepted and leaves the default and given data folders alone`() {
        val given = File(root, "given").apply { mkdirs() }
        File(given, "marker").writeText("user data")

        val child = runChild(args = listOf("--ui-smoke=${File(root, "report").path}"), dataDir = given)

        child.output shouldContain "STARTED"
        child.exitCode shouldBe 0
        val created = temporaryFolder.listFiles().orEmpty().toList()
        created shouldHaveSize 1
        created.single().name.startsWith(SmokeDataFolder.PREFIX) shouldBe true
        given.walk().map { it.relativeTo(given).path }.toSet() shouldBe setOf("", "marker")
        DesktopDirs.resolveDataDir(System.getProperty("os.name"), home.path, null, childEnv()::get).exists() shouldBe false
    }

    private val home: File get() = File(root, "home").apply { mkdirs() }
    private val temporaryFolder: File get() = File(root, "temp").apply { mkdirs() }

    /** The child's environment: the default data folder below [home] on every system. */
    private fun childEnv(): Map<String, String> = System.getenv().filterKeys { it != "XDG_DATA_HOME" } +
        ("APPDATA" to File(home, "AppData/Roaming").path)

    private class Child(val exitCode: Int, val output: String)

    private fun runChild(args: List<String>, dataDir: File? = null, tempDir: File = temporaryFolder): Child {
        val argFile = File(root, "child.args")
        val classPath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"")
        argFile.writeText("-cp\n\"$classPath\"\n")
        val command = buildList {
            add(File(System.getProperty("java.home"), "bin/java").path)
            add("-Djava.awt.headless=true")
            add("-Duser.home=${home.path}")
            add("-Djava.io.tmpdir=${tempDir.path}")
            dataDir?.let { add("-D${DesktopDirs.DATA_DIR_PROPERTY}=${it.path}") }
            add("@${argFile.path}")
            add(UiSmokeChild::class.java.name)
            addAll(args)
        }
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().apply {
            clear()
            putAll(childEnv())
        }
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(CHILD_TIMEOUT_S, TimeUnit.SECONDS)) { "The child did not exit: $output" }
        return Child(process.exitValue(), output)
    }

    private companion object {
        const val CHILD_TIMEOUT_S = 60L
    }
}
