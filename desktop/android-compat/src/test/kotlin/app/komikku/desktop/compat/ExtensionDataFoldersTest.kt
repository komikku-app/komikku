package app.komikku.desktop.compat

import android.app.Application
import android.content.Context
import extprobe.PathProbe
import io.kotest.assertions.withClue
import io.kotest.matchers.file.shouldStartWithPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExtensionDataFoldersTest {

    @TempDir
    lateinit var dir: File

    private val appData by lazy { dir.resolve("app") }
    private val app by lazy { Application(appData) }

    @Suppress("UNCHECKED_CAST")
    private fun pathsAsSeenBy(loader: ClassLoader): Map<String, File?> {
        val probe = Class.forName(PathProbe::class.java.name, true, loader)
        check(probe.classLoader === loader)
        val instance = probe.getDeclaredConstructor().newInstance()
        return probe.getMethod("paths", Context::class.java).invoke(instance, app) as Map<String, File?>
    }

    @Test
    fun `every path an extension asks a context for is inside its own data folder`() {
        val extensionData = dir.resolve("extensions-data/eu.kanade.tachiyomi.extension.en.probe")

        val paths = pathsAsSeenBy(ProbeLoader(extensionData))

        paths.forEach { (name, path) ->
            path shouldNotBe null
            withClue(name) { path!!.shouldStartWithPath(extensionData) }
        }
        paths.getValue("dataDir") shouldBe extensionData
        paths.getValue("applicationInfo.dataDir") shouldBe extensionData
        appData.exists() shouldBe false
    }

    @Test
    fun `host code keeps the app's folders`() {
        val paths = PathProbe().paths(app)

        paths.getValue("dataDir") shouldBe appData
        paths.getValue("cacheDir") shouldBe appData.resolve("cache")
        paths.getValue("applicationInfo.dataDir") shouldBe appData
    }

    @Test
    fun `a loader without a data folder gets the app's folders`() {
        pathsAsSeenBy(ProbeLoader(null)).getValue("cacheDir") shouldBe appData.resolve("cache")
    }

    /** Defines `extprobe.*` itself (child first) and owns [extensionDataDir], like an extension's class loader. */
    private class ProbeLoader(override val extensionDataDir: File?) :
        ClassLoader(ExtensionDataFoldersTest::class.java.classLoader),
        ExtensionDataOwner {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("extprobe.")) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
    }
}
