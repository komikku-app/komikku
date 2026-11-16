package app.komikku.desktop.extension.apk

import app.komikku.desktop.extension.testing.AxmlWriter
import app.komikku.desktop.extension.testing.FixtureApk
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class ApkManifestTest {

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `parses the extension manifest from UTF-8 and UTF-16 string pools`(utf8: Boolean) {
        val bytes = FixtureApk(
            packageName = "eu.kanade.tachiyomi.extension.all.fixture",
            versionName = "1.4.12",
            versionCode = 12,
            sourceClass = ".FixtureFactory",
            factory = "eu.kanade.tachiyomi.extension.all.fixture.FixtureFactory",
            extensionName = "Fixture ✓",
            extensionLib = 1.4f,
            nsfw = 1,
            contentWarning = 2,
            utf8 = utf8,
        ).manifest()

        val manifest = ApkManifest.parse(bytes)

        manifest.packageName shouldBe "eu.kanade.tachiyomi.extension.all.fixture"
        manifest.versionName shouldBe "1.4.12"
        manifest.versionCode shouldBe 12L
        manifest.label shouldBe "Tachiyomi: Fixture"
        manifest.usesFeatures shouldContainExactly listOf("tachiyomi.extension")
        with(manifest.metaData) {
            getString("tachiyomi.extension.class") shouldBe ".FixtureFactory"
            getString("tachiyomi.extension.factory") shouldBe "eu.kanade.tachiyomi.extension.all.fixture.FixtureFactory"
            getString("tachiyomix.name") shouldBe "Fixture ✓"
            getFloat("tachiyomix.extensionLib") shouldBe 1.4f
            getInt("tachiyomi.extension.nsfw") shouldBe 1
            getInt("tachiyomix.contentWarning") shouldBe 2
            // Bundle semantics for other types / missing keys
            getString("tachiyomix.extensionLib") shouldBe null
            getInt("tachiyomi.extension.class") shouldBe 0
            getFloat("missing") shouldBe 0f
        }
    }

    @Test
    fun `matches android attributes by resource id even without names`() {
        val bytes = AxmlWriter().write(
            AxmlWriter.Node(
                "manifest",
                listOf(
                    AxmlWriter.Attr(AxmlWriter.ANDROID_NS, "", AxmlWriter.ATTR_VERSION_CODE, null, AxmlWriter.TYPE_INT_DEC, 7),
                    AxmlWriter.Attr(AxmlWriter.ANDROID_NS, "x", AxmlWriter.ATTR_VERSION_CODE_MAJOR, null, AxmlWriter.TYPE_INT_DEC, 1),
                    AxmlWriter.plainString("package", "a.b"),
                ),
                listOf(AxmlWriter.Node("application")),
            ),
        )

        val manifest = ApkManifest.parse(bytes)

        manifest.packageName shouldBe "a.b"
        manifest.versionCode shouldBe (1L shl 32) + 7
        manifest.versionName shouldBe null
        manifest.label shouldBe null
    }

    @Test
    fun `resolves the label and the highest density icon through resources arsc`(@TempDir dir: File) {
        val mdpi = byteArrayOf(1, 2, 3)
        val xxxhdpi = byteArrayOf(4, 5, 6, 7)
        val apk = File(dir, "fixture.apk")
        FixtureApk(
            labelFromResources = "Tachiyomi: From resources",
            icons = mapOf(160 to mdpi, 640 to xxxhdpi),
        ).writeUnsigned(apk)

        val info = ApkInspector.inspect(apk)

        info.manifest.label shouldBe null
        info.label shouldBe "Tachiyomi: From resources"
        info.icon?.toList() shouldBe xxxhdpi.toList()
        info.signatures shouldBe ApkSignatureResult.Unsigned
        info.sha256 shouldBe sha256Hex(apk.readBytes())
    }
}
