package app.komikku.desktop.extension

import app.komikku.desktop.compat.ExtensionDataOwner
import app.komikku.desktop.extension.dex.DexJarConverter
import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * The whole pipeline on a generated APK: ASM class → jar → AOSP dx → `classes.dex` → signed APK →
 * inspect → dex2jar + stack map frames (cached) → extension class loader → `Source` instance.
 */
class DexPipelineTest {

    @TempDir
    lateinit var dir: File

    private fun fixtureApk(): File {
        val work = File(dir, "work").apply { mkdirs() }
        val jar = File(work, "classes.jar")
        FixtureClasses.jar(
            jar,
            mapOf(
                SOURCE_CLASS to FixtureClasses.source(SOURCE_CLASS, id = 4242L, name = "Dex fixture"),
            ) + FixtureClasses.branching(PACKAGE_PATH),
        )
        return File(dir, "dexfixture.apk").also { apk ->
            FixtureApk(
                packageName = "eu.kanade.tachiyomi.extension.en.dexfixture",
                sourceClass = ".DexFixtureSource",
                dex = FixtureClasses.dex(jar, work),
                extraEntries = mapOf(ASSET to "greeting=hello".toByteArray()),
            ).writeSigned(apk, TestSigningKey.rsa())
        }
    }

    @Test
    fun `loads a source from a signed APK with dex code`() {
        val runtime = ExtensionRuntime(File(dir, "extensions"))
        val inspected = runtime.inspect(fixtureApk())
        inspected.shouldBeInstanceOf<ExtensionPackageResult.Valid>()

        val loaded = runtime.load(inspected.extension)

        loaded.failedMethods shouldBe emptyList()
        loaded.sources.map { it.id } shouldContainExactly listOf(4242L)
        loaded.sources.single().name shouldBe "Dex fixture"
        loaded.sources.single().lang shouldBe "en"
        val cached = File(runtime.jarCacheDir, "${inspected.extension.apk.sha256}-v${DexJarConverter.CACHE_VERSION}.jar")
        cached.isFile shouldBe true
        loaded.jar shouldBe cached
        // Paths handed to the extension's code lie in its own folder, never the app's data folder (ANSWER 010)
        (loaded.classLoader as ExtensionDataOwner).extensionDataDir shouldBe
            File(dir, "extensions-data/eu.kanade.tachiyomi.extension.en.dexfixture").canonicalFile
        // APK entries are resources of the extension's class loader, like on Android (Keiyoushi's i18n assets)
        loaded.classLoader.getResourceAsStream(ASSET)!!.use { it.readBytes().decodeToString() } shouldBe "greeting=hello"
        ZipFile(cached).use { zip -> zip.getEntry("classes.dex") shouldBe null }

        // Second load: converted jar reused
        val modified = cached.lastModified()
        runtime.load(inspected.extension).sources.single().id shouldBe 4242L
        cached.lastModified() shouldBe modified

        runtime.pruneJarCache(emptySet())
        cached.exists() shouldBe false
    }

    @Test
    fun `every converted class passes the bytecode verifier`() {
        val runtime = ExtensionRuntime(File(dir, "extensions"))
        val extension = (runtime.inspect(fixtureApk()) as ExtensionPackageResult.Valid).extension
        val jar = runtime.load(extension).jar

        val verification = ExtensionRuntime.verifyClasses(jar)

        verification.rejected.shouldBeEmpty()
        verification.unresolved.shouldBeEmpty()
        verification.verified shouldBe 7
        ZipFile(jar).use { zip ->
            zip.entries().asSequence().filter { it.name.endsWith(".class") }.count() shouldBe 7
        }
        URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader).use { loader ->
            val branches = loader.loadClass("${PACKAGE_PATH.replace('/', '.')}.Branches")
            val fromHost = branches.getMethod("fromHost", Boolean::class.javaPrimitiveType)
            val fromExtension = branches.getMethod("fromExtension", Boolean::class.javaPrimitiveType)
            fromHost.invoke(null, true) shouldBe 1
            fromHost.invoke(null, false) shouldBe 1
            fromExtension.invoke(null, true) shouldBe 3
            fromExtension.invoke(null, false) shouldBe 3
            branches.getMethod("zeroDuration").invoke(null) shouldBe 0L
            val singleton = loader.loadClass("${PACKAGE_PATH.replace('/', '.')}.Singleton")
            singleton.getMethod("instanceValue").invoke(null) shouldBe 3
            singleton.getField("INSTANCE").get(null).javaClass shouldBe singleton
            val derived = loader.loadClass("${PACKAGE_PATH.replace('/', '.')}.Derived")
            val instance = derived.getConstructor(Int::class.javaPrimitiveType).newInstance(7)
            derived.getMethod("number").invoke(instance) shouldBe 7
            derived.getMethod("value").invoke(instance) shouldBe 3
        }
    }

    @Test
    fun `an inlined constructor of a class's own type inside its constructor is re-targeted`() {
        val work = File(dir, "self").apply { mkdirs() }
        val jar = File(work, "classes.jar")
        val base = "$PACKAGE_PATH/Base"
        val node = "$PACKAGE_PATH/Node"
        FixtureClasses.jar(
            jar,
            mapOf(
                SOURCE_CLASS to FixtureClasses.source(SOURCE_CLASS, id = 4242L, name = "Dex fixture"),
                base to FixtureClasses.emptyClass(base),
                node to FixtureClasses.selfConstructing(node, base),
            ),
        )
        val apk = File(dir, "self.apk")
        FixtureApk(
            packageName = "eu.kanade.tachiyomi.extension.en.dexfixture",
            sourceClass = ".DexFixtureSource",
            dex = FixtureClasses.dex(jar, work),
        ).writeSigned(apk, TestSigningKey.rsa())
        val runtime = ExtensionRuntime(File(dir, "extensions"))
        val extension = (runtime.inspect(apk) as ExtensionPackageResult.Valid).extension

        val converted = runtime.load(extension).jar

        val verification = ExtensionRuntime.verifyClasses(converted)
        verification.rejected.shouldBeEmpty()
        verification.verified shouldBe 3
        URLClassLoader(arrayOf(converted.toURI().toURL()), javaClass.classLoader).use { loader ->
            val nodeClass = loader.loadClass(node.replace('/', '.'))
            val instance = nodeClass.getConstructor(Int::class.javaPrimitiveType).newInstance(5)
            nodeClass.getMethod("number").invoke(instance) shouldBe 5
            nodeClass.getMethod("childNumber").invoke(instance) shouldBe 0
            nodeClass.getField("child").get(instance).javaClass shouldBe nodeClass
        }
    }

    @Test
    fun `jars of the old cache version are converted again and pruned`() {
        val runtime = ExtensionRuntime(File(dir, "extensions"))
        val extension = (runtime.inspect(fixtureApk()) as ExtensionPackageResult.Valid).extension
        runtime.jarCacheDir.mkdirs()
        // Version 1 cache entry (no stack map frames): `<sha256>.jar`
        val stale = File(runtime.jarCacheDir, "${extension.apk.sha256}.jar").apply { writeText("not a jar") }

        val loaded = runtime.load(extension)

        loaded.jar shouldBe runtime.cachedJar(extension)
        loaded.sources.single().id shouldBe 4242L
        runtime.pruneJarCache(setOf(extension.apk.sha256))
        stale.exists() shouldBe false
        loaded.jar.isFile shouldBe true
    }

    private companion object {
        const val PACKAGE_PATH = "eu/kanade/tachiyomi/extension/en/dexfixture"
        const val SOURCE_CLASS = "$PACKAGE_PATH/DexFixtureSource"
        const val ASSET = "assets/i18n/messages_en.properties"
    }
}
