package app.komikku.desktop.compat

import android.net.Uri
import androidx.core.net.toFile
import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * R.1 (REVIEW_STEP_D1.md): Windows paths in `file:` URIs. The Windows cases pass the separator, so they run on every
 * OS; the round trips at the end use the OS the tests run on.
 */
class FileUrisTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `a Windows path is written with forward slashes after a slash, like File toURI`() {
        FileUris.uriPath("C:\\Users\\runner\\a b\\漫画", WINDOWS) shouldBe "/C:/Users/runner/a b/漫画"
        FileUris.uriPath("\\\\server\\share\\x", WINDOWS) shouldBe "//server/share/x"
        FileUris.uriPath("/home/me/a\\b", POSIX) shouldBe "/home/me/a\\b"
    }

    @Test
    fun `a Windows file URI reads back as the drive path`() {
        val path = "C:\\Users\\runner\\AppData\\Roaming\\Komikku\\storage\\automatic"
        val uri = Uri.parse(windowsUri(path))
        uri.toString() shouldBe "file:///C%3A/Users/runner/AppData/Roaming/Komikku/storage/automatic"
        uri.authority shouldBe ""
        FileUris.filePath(uri, WINDOWS) shouldBe "C:/Users/runner/AppData/Roaming/Komikku/storage/automatic"
        FileUris.filePath(Uri.parse(windowsUri("D:\\")), WINDOWS) shouldBe "D:/"
        FileUris.filePath(Uri.parse(windowsUri("\\\\server\\share\\x")), WINDOWS) shouldBe "//server/share/x"
        // A path that only looks like a drive after the first folder stays as it is
        FileUris.filePath(Uri.parse("file:///home/C:/x"), WINDOWS) shouldBe "/home/C:/x"
    }

    @Test
    fun `the form earlier versions stored on Windows is still read`() {
        // `Uri.fromFile` used to encode the backslashes, so the whole path parsed as the authority
        val stored = "file://" + Uri.encode("C:\\Users\\runner\\Komikku\\storage", "/")
        stored shouldBe "file://C%3A%5CUsers%5Crunner%5CKomikku%5Cstorage"
        val uri = Uri.parse(stored)
        uri.path shouldBe ""
        FileUris.filePath(uri, WINDOWS) shouldBe "C:\\Users\\runner\\Komikku\\storage"
        // Not on macOS / Linux, where it never was written
        FileUris.filePath(uri, POSIX) shouldBe ""
        FileUris.filePath(Uri.parse("file://host"), WINDOWS) shouldBe null
    }

    @Test
    fun `only file URIs name a file`() {
        FileUris.filePath(Uri.parse("content://media/x"), WINDOWS) shouldBe null
        FileUris.filePath(Uri.parse("https://example.com/a"), POSIX) shouldBe null
        FileUris.file("relative/path") shouldBe null
        FileUris.filePath(Uri.parse("file:///home/me/a%20b"), POSIX) shouldBe "/home/me/a b"
    }

    @Test
    fun `a file survives the round trip through its URI on this OS`() {
        val file = File(dir, "storage dir/automatic/ä 漫画.tachibk").apply {
            parentFile.mkdirs()
            writeText("backup")
        }
        val uri = Uri.fromFile(file)
        val parsed = Uri.parse(uri.toString())
        FileUris.file(parsed) shouldBe file.absoluteFile
        parsed.toFile() shouldBe file.absoluteFile
        UniFile.fromUri(null, parsed)!!.filePath shouldBe file.absolutePath
        UniFile.fromUri(null, Uri.fromFile(file.parentFile))!!.isDirectory shouldBe true
        android.content.ContentResolver(null).openInputStream(parsed)!!.use { it.readBytes().decodeToString() } shouldBe
            "backup"
    }

    /** What `Uri.fromFile` writes for [path] on Windows. */
    private fun windowsUri(path: String) = "file://" + Uri.encode(FileUris.uriPath(path, WINDOWS), "/")

    private companion object {
        const val WINDOWS = '\\'
        const val POSIX = '/'
    }
}
