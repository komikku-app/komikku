package app.komikku.desktop.selftest

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * M2: the packaged app's self-test. Here it runs on the build JDK (where every module is there), so it checks that the
 * checks themselves work and that the bundled module list keeps what the app and its extensions need.
 */
class DesktopSelfTestTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `the module list keeps the modules the packaged app needs`() {
        val listed = DesktopSelfTest.parseModules(resourceText(DesktopSelfTest.MODULES_RESOURCE))
        // The first Mac test build lacked jdk.zipfs (dex2jar); the others are reached by service loaders / reflection
        listed shouldContainAll REQUIRED
        // Every standard java.* module, which an extension may use
        listed shouldContainAll ModuleLayer.boot().modules().map { it.name }.filter { it.startsWith("java.") }
        listed.toSet().size shouldBe listed.size
    }

    @Test
    fun `parses the module list format`() {
        DesktopSelfTest.parseModules("# comment\n\njava.base\n  jdk.zipfs  # why\n") shouldContainExactly
            listOf("java.base", "jdk.zipfs")
    }

    @Test
    fun `recognises the argument and its report file`() {
        DesktopSelfTest.isRequested(arrayOf()) shouldBe false
        DesktopSelfTest.isRequested(arrayOf("--self-testing")) shouldBe false
        DesktopSelfTest.isRequested(arrayOf("--self-test")) shouldBe true
        DesktopSelfTest.reportFile(arrayOf("--self-test")) shouldBe null
        DesktopSelfTest.reportFile(arrayOf("--self-test=/tmp/r.txt")) shouldBe File("/tmp/r.txt")
    }

    @Test
    fun `every check passes on the build JDK and the report says so`() {
        val report = File(dir, "out/report.txt")
        val outcomes = DesktopSelfTest.run()
        outcomes.filterNot { it.passed }.map { it.toString() } shouldBe emptyList()
        DesktopSelfTest.main(report) shouldBe 0
        val lines = report.readLines()
        lines.last() shouldBe DesktopSelfTest.PASSED
        lines.drop(1).dropLast(1).map { it.substringAfter(" ").substringBefore(":").trim() } shouldContainExactly
            DesktopSelfTest.checks.map { it.name }
        lines.first() shouldEndWith System.getProperty("os.arch")
    }

    private fun resourceText(path: String) = DesktopSelfTest::class.java.getResourceAsStream(path)!!.readBytes().decodeToString()

    private companion object {
        val REQUIRED = listOf(
            "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.net.http",
            "java.prefs", "java.scripting", "java.security.jgss", "java.sql", "java.xml", "jdk.charsets",
            "jdk.crypto.ec", "jdk.localedata", "jdk.naming.dns", "jdk.security.auth", "jdk.unsupported", "jdk.zipfs",
        )
    }
}
