package app.komikku.desktop.extension.linkcheck

import app.komikku.desktop.extension.dex.DexJarConverter
import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** Registered in `META-INF/services` of the tests: the fixtures' sources need nothing from the host. */
class TestHostEnvironment : ExtensionHostEnvironment {
    override fun prepare(dataDir: File) {
        prepared = dataDir
    }

    companion object {
        var prepared: File? = null
    }
}

class CatalogueCheckTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `reads the APK URLs of a gzip-compressed protobuf index and of a JSON index`() {
        val first = "https://github.com/keiyoushi/extensions/releases/download/x/tachiyomi-en.a-v1.4.1.apk"
        val second = "https://github.com/keiyoushi/extensions/releases/download/x/tachiyomi-all.b-v1.4.2.apk"
        // Protobuf strings: a tag and a length byte before each URL; the second length byte is printable ('h')
        val protobuf = byteArrayOf(0x0a, first.length.toByte()) + first.toByteArray() +
            byteArrayOf(0x12, 'h'.code.toByte()) + second.toByteArray() + byteArrayOf(0x1a, 0x02) + first.toByteArray()
        val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(protobuf) } }.toByteArray()

        CatalogueIndex.apkUrls("https://example.invalid/index.pb", gzip) shouldContainExactly listOf(first, second)

        val json = """[{"name":"A","pkg":"x","apk":"tachiyomi-en.a-v1.4.1.apk"},{"apk": "tachiyomi-all.b-v1.4.2.apk"}]"""
        CatalogueIndex.apkUrls("https://example.invalid/repo/index.min.json", json.toByteArray()) shouldContainExactly listOf(
            "https://example.invalid/repo/apk/tachiyomi-en.a-v1.4.1.apk",
            "https://example.invalid/repo/apk/tachiyomi-all.b-v1.4.2.apk",
        )
    }

    private fun apk(pkg: String, constructorCall: Pair<String, String>? = null): File {
        val work = File(dir, "work-$pkg").apply { mkdirs() }
        val className = pkg.replace('.', '/') + "/FixtureSource"
        val jar = File(work, "classes.jar")
        FixtureClasses.jar(
            jar,
            mapOf(
                className to FixtureClasses.source(className, 1L, "Fixture", constructorCall = constructorCall),
                "$className\$Unused" to FixtureClasses.classCalling(
                    "$className\$Unused",
                    "android/graphics/BitmapFactory",
                    "decodeNothing",
                    "()V",
                ),
            ),
        )
        return File(dir, "$pkg.apk").also { apk ->
            FixtureApk(packageName = pkg, sourceClass = ".FixtureSource", dex = FixtureClasses.dex(jar, work))
                .writeSigned(apk, TestSigningKey.rsa())
        }
    }

    private fun report(apk: File): String {
        val loader = javaClass.classLoader
        val checks = ExtensionChecks(verify = true, createSources = true, compatUsage = true, loader, File(dir, "data"))
        val jar = File(dir, apk.nameWithoutExtension + ".jar").also { DexJarConverter.convert(apk, it) }
        val links = LinkChecker(loader, trackHostClass = checks::isCompatClass).check(jar)
        return StringBuilder(links.format(apk.name)).also { checks.appendTo(it, apk, jar, links) }.toString()
    }

    @Test
    fun `verifies, creates the sources and lists the compat classes an extension uses`() {
        val looping = report(apk("eu.kanade.tachiyomi.extension.en.looping", "android/os/Looper" to "loop"))
        val plain = report(apk("eu.kanade.tachiyomi.extension.en.plain"))

        looping shouldContain "android.graphics.BitmapFactory  <- "
        // The missing class is only referenced in a method body: the classes still link
        looping shouldContain "Verification: 2 verified, 0 rejected, 0 unresolved"
        looping shouldContain "Sources: unsupported on desktop: needs android.os.Looper.loop()"
        looping shouldContain "Compat classes used: android.os.Looper"
        plain shouldContain "Sources: 1 created"
        plain shouldContain "Compat classes used: none"
        TestHostEnvironment.prepared shouldBe File(dir, "data/app")

        val summary = CatalogueSummary.summarize((looping + "\n" + plain).lineSequence())
        summary shouldContain "| Checked | 2 |"
        summary shouldContain "| Every class verifies | 2 |"
        summary shouldContain "| Sources created | 1 of 2 |"
        summary shouldContain "| Sources not created: unsupported on desktop | 1 |"
        summary shouldContain "| Reference a missing `android.*` class anywhere | 2 |"
        summary shouldContain "| `android.graphics.BitmapFactory` | 2 | 1 | eu.kanade.tachiyomi.extension.en.looping.apk, " +
            "eu.kanade.tachiyomi.extension.en.plain.apk |"
        summary shouldContain "| `android.os.Looper` | 1 |"
    }

    @Test
    fun `summarises failed conversions and untranslated methods`() {
        val report = """
            (2 method(s) of b.apk could not be translated by dex2jar)
            == a.apk: failed: java.io.IOException: truncated
            == b.apk: 10 classes checked
            All references resolve.
        """.trimIndent()

        val summary = CatalogueSummary.summarize(report.lineSequence())

        summary shouldContain "| Converted (dex2jar) | 1 |"
        summary shouldContain "| … with every method translated | 0 |"
        summary shouldContain "| Every reference resolves (link check, method bodies included) | 1 |"
        summary shouldContain "- a.apk: java.io.IOException: truncated"
    }
}
