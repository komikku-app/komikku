package android.content

import android.app.Application
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ApplicationInfoTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `application info names the package and the data folder`() {
        val info = Application(dir).applicationInfo

        info.packageName shouldBe Application.DEFAULT_PACKAGE_NAME
        info.dataDir shouldBe dir.absolutePath
        File(info.dataDir, "shared_prefs").exists() shouldBe false
    }
}
