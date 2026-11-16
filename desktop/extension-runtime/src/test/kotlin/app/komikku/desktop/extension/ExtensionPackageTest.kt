package app.komikku.desktop.extension

import app.komikku.desktop.extension.apk.ApkInspector
import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtensionPackageTest {

    @TempDir
    lateinit var dir: File

    private lateinit var key: TestSigningKey

    @BeforeAll
    fun setUp() {
        key = TestSigningKey.rsa()
    }

    private fun check(fixture: FixtureApk, signed: Boolean = true): ExtensionPackageResult {
        val apk = File(dir, "${System.nanoTime()}.apk")
        if (signed) fixture.writeSigned(apk, key) else fixture.writeUnsigned(apk)
        return ExtensionPackage.from(ApkInspector.inspect(apk))
    }

    @Test
    fun `valid extension`() {
        val result = check(
            FixtureApk(
                packageName = "eu.kanade.tachiyomi.extension.all.fixture",
                versionName = "1.4.7",
                versionCode = 7,
                sourceClass = ".FixtureFactory; eu.kanade.tachiyomi.extension.all.other.Other",
                contentWarning = 1,
            ),
        )

        result.shouldBeInstanceOf<ExtensionPackageResult.Valid>()
        with(result.extension) {
            name shouldBe "Fixture"
            pkgName shouldBe "eu.kanade.tachiyomi.extension.all.fixture"
            versionName shouldBe "1.4.7"
            versionCode shouldBe 7L
            libVersion shouldBe 1.4
            isNsfw shouldBe true
            sourceClassNames shouldContainExactly listOf(
                "eu.kanade.tachiyomi.extension.all.fixture.FixtureFactory",
                "eu.kanade.tachiyomi.extension.all.other.Other",
            )
            signatures shouldContainExactly listOf(key.sha256)
            signatureHash shouldBe key.sha256
        }
    }

    @Test
    fun `name from tachiyomix name and lib version from the version name`() {
        val result = check(FixtureApk(extensionName = "Named", extensionLib = null, versionName = "1.6.20", nsfw = 0))

        result.shouldBeInstanceOf<ExtensionPackageResult.Valid>()
        result.extension.name shouldBe "Named"
        result.extension.libVersion shouldBe 1.6
        result.extension.isNsfw shouldBe false
    }

    @Test
    fun `legacy nsfw flag`() {
        val result = check(FixtureApk(nsfw = 1))

        result.shouldBeInstanceOf<ExtensionPackageResult.Valid>()
        result.extension.isNsfw shouldBe true
    }

    @Test
    fun `rejections match ExtensionLoader`() {
        check(FixtureApk(isExtension = false)).rejection() shouldContain "not an extension"
        check(FixtureApk(versionName = null)).rejection() shouldContain "Missing versionName"
        check(FixtureApk(extensionLib = null, versionName = "1.5.1")).rejection() shouldContain "Lib version is 1.5"
        check(FixtureApk(extensionLib = 1.3f)).rejection() shouldContain "Lib version is 1.3"
        check(FixtureApk(), signed = false).rejection() shouldContain "isn't signed"
        check(FixtureApk(sourceClass = null)).rejection() shouldContain "no source class"
    }

    private fun ExtensionPackageResult.rejection(): String {
        shouldBeInstanceOf<ExtensionPackageResult.Rejected>()
        return reason
    }
}
