package tachiyomi.core.common.desktop

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class DesktopDirsTest {

    private val home = "/home/user"

    private fun resolve(
        osName: String,
        env: Map<String, String> = emptyMap(),
        override: String? = null,
    ): File = DesktopDirs.resolveDataDir(osName, home, override, env::get)

    @Test
    fun `windows uses APPDATA`() {
        resolve("Windows 11", mapOf("APPDATA" to "C:/Users/u/AppData/Roaming")) shouldBe
            File("C:/Users/u/AppData/Roaming", "Komikku")
    }

    @Test
    fun `windows falls back to the roaming profile dir`() {
        resolve("Windows 10") shouldBe File("$home/AppData/Roaming", "Komikku")
    }

    @Test
    fun `macOS uses Application Support`() {
        resolve("Mac OS X") shouldBe File(home, "Library/Application Support/Komikku")
    }

    @Test
    fun `linux uses XDG_DATA_HOME when absolute`() {
        resolve("Linux", mapOf("XDG_DATA_HOME" to "/data/xdg")) shouldBe File("/data/xdg", "komikku")
    }

    @Test
    fun `linux ignores relative XDG_DATA_HOME`() {
        resolve("Linux", mapOf("XDG_DATA_HOME" to "relative/dir")) shouldBe File(home, ".local/share/komikku")
    }

    @Test
    fun `linux falls back to local share`() {
        resolve("Linux") shouldBe File(home, ".local/share/komikku")
    }

    @Test
    fun `override wins on every OS`() {
        resolve("Windows 11", override = "/portable/data") shouldBe File("/portable/data")
        resolve("Linux", mapOf("XDG_DATA_HOME" to "/data/xdg"), override = "/portable/data") shouldBe
            File("/portable/data")
    }
}
