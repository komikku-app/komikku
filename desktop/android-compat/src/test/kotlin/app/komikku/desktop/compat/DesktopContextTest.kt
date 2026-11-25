package app.komikku.desktop.compat

import android.app.Application
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DesktopContextTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `folder getters create their folders on access`() {
        val app = Application(dir.resolve("data"))

        app.cacheDir.isDirectory shouldBe true
        app.filesDir.isDirectory shouldBe true
        app.getExternalFilesDir("covers")!!.isDirectory shouldBe true
        app.getDir("webview", 0).isDirectory shouldBe true
        app.getDatabasePath("x.db").parentFile.isDirectory shouldBe true
        app.dataDir shouldBe dir.resolve("data")
    }

    @Test
    fun `once the data folder is closed the getters return the same paths without creating folders`() {
        val dataDir = dir.resolve("data")
        val app = Application(dataDir)
        val cacheDir = app.cacheDir

        app.stopCreatingDataFolders()
        dataDir.deleteRecursively() shouldBe true

        app.cacheDir shouldBe cacheDir
        app.externalCacheDir shouldBe cacheDir
        app.filesDir shouldBe dataDir.resolve("files")
        app.getExternalFilesDir("covers") shouldBe dataDir.resolve("files/covers")
        app.getDir("webview", 0) shouldBe dataDir.resolve("app_webview")
        app.getDatabasePath("x.db") shouldBe dataDir.resolve("databases/x.db")
        app.dataDir shouldBe dataDir
        dataDir.exists() shouldBe false
    }

    @Test
    fun `closing one data folder leaves another application's folders alone`() {
        val closed = Application(dir.resolve("closed"))
        val open = Application(dir.resolve("open"))

        closed.stopCreatingDataFolders()

        open.cacheDir.isDirectory shouldBe true
        dir.resolve("closed").exists() shouldBe false
    }
}
