package app.komikku.desktop.extension.loader

import app.komikku.desktop.extension.ExtensionLoadException
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.desktop.extension.fixture.KotlinFixtureSource
import app.komikku.desktop.extension.testing.FixtureClasses
import eu.kanade.tachiyomi.source.Source
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExtensionClassLoaderTest {

    @TempDir
    lateinit var dir: File

    private val fixturePackage = "app/komikku/desktop/extension/fixture/"

    /** A jar with the compiled `fixture` package of the test classpath plus [extra] classes. */
    private fun fixtureJar(extra: Map<String, ByteArray> = emptyMap()): File {
        val directory = File(javaClass.classLoader.getResource(fixturePackage)!!.toURI())
        val classes = directory.listFiles()!!
            .filter { it.extension == "class" }
            .associate { fixturePackage + it.nameWithoutExtension to it.readBytes() }
        return File(dir, "fixture.jar").also { FixtureClasses.jar(it, classes + extra) }
    }

    @Test
    fun `instantiates sources and factories like ExtensionLoader, child first`() {
        val loader = ExtensionClassLoader(fixtureJar(), javaClass.classLoader)

        val sources = ExtensionRuntime.instantiateSources(
            listOf(
                "app.komikku.desktop.extension.fixture.KotlinFixtureSource",
                "app.komikku.desktop.extension.fixture.KotlinFixtureFactory",
            ),
            loader,
        )

        sources.map { it.id } shouldContainExactly listOf(1001L, 2001L, 2002L)
        sources.map { it.name } shouldContainExactly listOf("Kotlin fixture", "Factory A", "Factory B")
        // The extension's classes are its own (child first)...
        sources.forEach { it.javaClass.classLoader shouldBeSameInstanceAs loader }
        sources.first().javaClass shouldNotBe KotlinFixtureSource::class.java
        // ...while the source API is the host's, so the host can use them as `Source`.
        loader.loadClass(Source::class.java.name) shouldBeSameInstanceAs Source::class.java
        runBlocking { sources.first().getPopularManga(2) }.mangas.single().title shouldBe "Popular 2"
    }

    @Test
    fun `shared packages come from the host, unless the host lacks the class`() {
        val loader = ExtensionClassLoader(
            fixtureJar(
                mapOf(
                    // A shadowing copy of a host class: the host's must win
                    "okhttp3/OkHttpClient" to FixtureClasses.emptyClass("okhttp3/OkHttpClient"),
                    // An extension-private class in a shared package: loaded from the extension
                    "eu/kanade/tachiyomi/util/ExtensionPrivateHelper" to
                        FixtureClasses.emptyClass("eu/kanade/tachiyomi/util/ExtensionPrivateHelper"),
                ),
            ),
            javaClass.classLoader,
        )

        loader.loadClass("okhttp3.OkHttpClient") shouldBeSameInstanceAs OkHttpClient::class.java
        loader.loadClass("eu.kanade.tachiyomi.util.ExtensionPrivateHelper").classLoader shouldBeSameInstanceAs loader
        loader.loadClass("java.lang.String") shouldBeSameInstanceAs String::class.java
    }

    @Test
    fun `classes that are not sources fail the extension`() {
        val loader = ExtensionClassLoader(fixtureJar(), javaClass.classLoader)

        shouldThrow<ExtensionLoadException> {
            ExtensionRuntime.instantiateSources(listOf("app.komikku.desktop.extension.fixture.NotASource"), loader)
        }
        shouldThrow<ClassNotFoundException> {
            ExtensionRuntime.instantiateSources(listOf("app.komikku.desktop.extension.fixture.Missing"), loader)
        }
    }
}
