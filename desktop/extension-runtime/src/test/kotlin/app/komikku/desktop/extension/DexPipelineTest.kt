package app.komikku.desktop.extension

import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The whole pipeline on a generated APK: ASM class → jar → AOSP dx → `classes.dex` → signed APK →
 * inspect → dex2jar (cached) → extension class loader → `Source` instance.
 */
class DexPipelineTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `loads a source from a signed APK with dex code`() {
        val work = File(dir, "work").apply { mkdirs() }
        val jar = File(work, "classes.jar")
        FixtureClasses.jar(
            jar,
            mapOf(
                "eu/kanade/tachiyomi/extension/en/dexfixture/DexFixtureSource" to FixtureClasses.source(
                    "eu/kanade/tachiyomi/extension/en/dexfixture/DexFixtureSource",
                    id = 4242L,
                    name = "Dex fixture",
                ),
            ),
        )
        val apk = File(dir, "dexfixture.apk")
        FixtureApk(
            packageName = "eu.kanade.tachiyomi.extension.en.dexfixture",
            sourceClass = ".DexFixtureSource",
            dex = FixtureClasses.dex(jar, work),
        ).writeSigned(apk, TestSigningKey.rsa())

        val runtime = ExtensionRuntime(File(dir, "extensions"))
        val inspected = runtime.inspect(apk)
        inspected.shouldBeInstanceOf<ExtensionPackageResult.Valid>()

        val loaded = runtime.load(inspected.extension)

        loaded.failedMethods shouldBe emptyList()
        loaded.sources.map { it.id } shouldContainExactly listOf(4242L)
        loaded.sources.single().name shouldBe "Dex fixture"
        loaded.sources.single().lang shouldBe "en"
        val cached = File(runtime.jarCacheDir, "${inspected.extension.apk.sha256}.jar")
        cached.isFile shouldBe true

        // Second load: converted jar reused
        val modified = cached.lastModified()
        runtime.load(inspected.extension).sources.single().id shouldBe 4242L
        cached.lastModified() shouldBe modified

        runtime.pruneJarCache(emptySet())
        cached.exists() shouldBe false
    }
}
