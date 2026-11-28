package app.komikku.desktop.smoke

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** The smoke mode's data folder guard: one test per refusal (ANSWER 011, condition 4). */
class SmokeDataFolderTest {

    @TempDir
    lateinit var root: File

    private val tempRoot: Path get() = File(root, "tmp").apply { mkdirs() }.toPath()
    private val userData: Path get() = File(root, "user-data").apply { mkdirs() }.toPath()

    @Test
    fun `a new empty folder in the temporary folder is accepted`() {
        val folder = SmokeDataFolder.create(tempRoot)

        SmokeDataFolder.refusal(folder, tempRoot, listOf(userData)).shouldBeNull()
        folder.fileName.toString().startsWith(SmokeDataFolder.PREFIX) shouldBe true
    }

    @Test
    fun `a folder outside the temporary folder is refused`() {
        val folder = Files.createDirectories(root.toPath().resolve("elsewhere"))

        SmokeDataFolder.refusal(folder, tempRoot, listOf(userData)).shouldNotBeNull() shouldContain "not a new folder inside the temporary folder"
    }

    @Test
    fun `the temporary folder itself is refused`() {
        SmokeDataFolder.refusal(tempRoot, tempRoot, emptyList()).shouldNotBeNull() shouldContain "not a new folder"
    }

    @Test
    fun `the data folder itself is refused`() {
        val data = Files.createDirectories(tempRoot.resolve("data"))

        SmokeDataFolder.refusal(data, tempRoot, listOf(data)).shouldNotBeNull() shouldContain "is the data folder"
    }

    @Test
    fun `a folder inside the data folder is refused`() {
        val data = Files.createDirectories(tempRoot.resolve("data"))
        val inside = Files.createDirectories(data.resolve("komikku-ui-smoke-1"))

        SmokeDataFolder.refusal(inside, tempRoot, listOf(data)).shouldNotBeNull() shouldContain "lies inside the data folder"
    }

    @Test
    fun `a folder containing the data folder is refused`() {
        val outer = Files.createDirectories(tempRoot.resolve("komikku-ui-smoke-1"))
        val data = Files.createDirectories(outer.resolve("Komikku"))

        SmokeDataFolder.refusal(outer, tempRoot, listOf(data)).shouldNotBeNull() shouldContain "contains the data folder"
    }

    @Test
    fun `a link to the data folder is refused`() {
        val data = Files.createDirectories(tempRoot.resolve("data"))
        val link = runCatching { Files.createSymbolicLink(tempRoot.resolve("komikku-ui-smoke-link"), data) }.getOrNull()
            ?: return // no symbolic links (Windows without the privilege)

        SmokeDataFolder.refusal(link, tempRoot, listOf(data)).shouldNotBeNull() shouldContain "is the data folder"
    }

    @Test
    fun `a folder that is not empty is refused`() {
        val folder = SmokeDataFolder.create(tempRoot)
        File(folder.toFile(), "library.db").writeText("x")

        SmokeDataFolder.refusal(folder, tempRoot, listOf(userData)).shouldNotBeNull() shouldContain "is not empty"
    }

    @Test
    fun `the default data folder and a given one are protected`() {
        val given = File(root, "given").path
        val default = DesktopDirs.resolveDataDir(
            osName = System.getProperty("os.name"),
            userHome = System.getProperty("user.home"),
            override = null,
            env = System::getenv,
        ).toPath()

        val folders = SmokeDataFolder.protectedFolders(givenDataDir = given)

        folders shouldContain default
        folders shouldContain File(given).toPath()
        SmokeDataFolder.protectedFolders(givenDataDir = null) shouldBe listOf(default)
    }
}
