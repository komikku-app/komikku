package app.komikku.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ExtensionPathsTest {

    @TempDir
    lateinit var dir: File

    private val traversals = listOf(
        "../../evil",
        "eu.kanade/../../evil",
        "/absolute/evil",
        "..\\..\\evil",
        "C:\\evil",
        "\\\\server\\share\\evil",
        "eu.kanade.x/",
        "..",
        ".",
        "",
        "eu.kanade.evil\u0000.x",
    )

    @Test
    fun `Java package names are valid, anything else is not`() {
        ExtensionPaths.isValidPackageName("eu.kanade.tachiyomi.extension.en.mangadex") shouldBe true
        ExtensionPaths.isValidPackageName("a.B_9.c") shouldBe true
        ExtensionPaths.isValidPackageName("nodots") shouldBe false
        ExtensionPaths.isValidPackageName("eu.kanade.é") shouldBe false
        ExtensionPaths.isValidPackageName("eu.kanade.x\n") shouldBe false
        traversals.forEach { ExtensionPaths.isValidPackageName(it) shouldBe false }
    }

    @Test
    fun `a file named after a valid package stays in the folder`() {
        val apks = File(dir, "extensions/apks")

        val file = ExtensionPaths.packageFile(apks, "eu.kanade.tachiyomi.extension.en.x", ".apk")

        file shouldBe File(apks.canonicalFile, "eu.kanade.tachiyomi.extension.en.x.apk")
        ExtensionPaths.packageFile(apks, "eu.kanade.x", ".apk.tmp").name shouldBe "eu.kanade.x.apk.tmp"
        ExtensionPaths.packageFile(apks, "eu.kanade.x").name shouldBe "eu.kanade.x"
    }

    @Test
    fun `names that are not package names are refused before any path is built`() {
        val apks = File(dir, "extensions/apks")
        for (name in traversals) {
            shouldThrow<IllegalArgumentException> { ExtensionPaths.packageFile(apks, name, ".apk") }
            ExtensionPaths.packageFileOrNull(apks, name, ".apk").shouldBeNull()
        }
    }

    @Test
    fun `a file that resolves outside the folder is refused`() {
        val apks = File(dir, "extensions/apks").apply { mkdirs() }
        val outside = File(dir, "outside.apk").apply { writeText("not yours") }
        val link = File(apks, "eu.kanade.x.apk").toPath()
        try {
            Files.createSymbolicLink(link, outside.toPath())
        } catch (e: IOException) {
            assumeTrue(false, "symbolic links unavailable: $e")
        } catch (e: UnsupportedOperationException) {
            assumeTrue(false, "symbolic links unavailable: $e")
        }

        shouldThrow<IllegalArgumentException> { ExtensionPaths.packageFile(apks, "eu.kanade.x", ".apk") }
        ExtensionPaths.packageFileOrNull(apks, "eu.kanade.x", ".apk").shouldBeNull()
        outside.readText() shouldBe "not yours"
    }
}
