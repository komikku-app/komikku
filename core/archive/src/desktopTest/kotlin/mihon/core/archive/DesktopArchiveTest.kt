package mihon.core.archive

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.core.common.preference.DesktopPreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.fullType
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopArchiveTest {

    @TempDir
    lateinit var dir: File

    private lateinit var securityPreferences: SecurityPreferences

    private val context by lazy { Application(dir.resolve("app")) }

    private val pages = mapOf(
        "001.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3),
        "002.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 4, 5, 6),
        "ComicInfo.xml" to "<ComicInfo><Series>Test</Series></ComicInfo>".toByteArray(),
    )

    @BeforeAll
    fun setUpInjekt(@TempDir prefsDir: File) {
        securityPreferences = SecurityPreferences(DesktopPreferenceStore(prefsDir.resolve("security.json")))
        Injekt.addSingleton(fullType<SecurityPreferences>(), securityPreferences)
    }

    @Test
    fun `reads entries of a cbz`() {
        val cbz = dir.resolve("chapter.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("folder/"))
            zip.closeEntry()
            pages.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }

        UniFile.fromFile(cbz)!!.archiveReader(context).use { reader ->
            reader.encrypted shouldBe false
            reader.wrongPassword shouldBe null
            reader.assertContent(expectDirectory = "folder/")
        }
    }

    @Test
    fun `reads a cbz written by ZipWriter`() {
        val cbz = dir.resolve("written.cbz")
        val image = dir.resolve("003.webp").apply { writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46, 9)) }
        ZipWriter(context, UniFile.fromFile(cbz)!!).use { writer ->
            pages.forEach { (name, data) -> writer.write(data, name) }
            writer.write(UniFile.fromFile(image)!!)
        }

        ArchiveReader(cbz).use { reader ->
            reader.encrypted shouldBe false
            reader.getInputStream("003.webp")!!.use { it.readBytes() } shouldBe image.readBytes()
            reader.assertContent(extra = listOf("003.webp"))
        }
    }

    @ParameterizedTest
    @EnumSource(SecurityPreferences.EncryptionType::class)
    fun `reads a password protected cbz`(type: SecurityPreferences.EncryptionType) {
        securityPreferences.encryptionType().set(type)
        securityPreferences.cbzPassword().set(CbzCrypto.encryptCbz("s3cret-pässword"))
        val cbz = dir.resolve("encrypted-$type.cbz")
        ZipWriter(context, UniFile.fromFile(cbz)!!, encrypt = true).use { writer ->
            pages.forEach { (name, data) -> writer.write(data, name) }
        }

        ArchiveReader(cbz).use { reader ->
            reader.encrypted shouldBe true
            reader.wrongPassword shouldBe false
            reader.assertContent(encrypted = true)
        }

        // Another password: detected like libarchive's "Incorrect passphrase". ZipCrypto only has a
        // one-byte check value (a wrong password passes it with probability 1/256), so it is not
        // asserted there to keep the test deterministic.
        securityPreferences.cbzPassword().set(CbzCrypto.encryptCbz("other"))
        if (type != SecurityPreferences.EncryptionType.ZIP_STANDARD) {
            ArchiveReader(cbz).use { reader ->
                reader.encrypted shouldBe true
                reader.wrongPassword shouldBe true
            }
        }

        // No password at all: same error as Android.
        securityPreferences.cbzPassword().delete()
        assertThrows<IllegalStateException> { ArchiveReader(cbz) }
    }

    @Test
    fun `reads a gzipped tar`() {
        val cbt = dir.resolve("chapter.cbt")
        TarArchiveOutputStream(GzipCompressorOutputStream(cbt.outputStream())).use { tar ->
            pages.forEach { (name, data) ->
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }

        ArchiveReader(cbt).use { it.assertContent() }
    }

    @Test
    fun `reads a 7z archive`() {
        val cb7 = dir.resolve("chapter.cb7")
        SevenZOutputFile(cb7).use { sevenZ ->
            pages.forEach { (name, data) ->
                val source = dir.resolve("src-$name").apply { writeBytes(data) }
                sevenZ.putArchiveEntry(sevenZ.createArchiveEntry(source, name))
                sevenZ.write(data)
                sevenZ.closeArchiveEntry()
            }
        }

        ArchiveReader(cb7).use { it.assertContent() }
    }

    @Test
    fun `finds the images of an epub`() {
        val epub = dir.resolve("book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun add(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            add(
                "OEBPS/content.opf",
                """
                <package><manifest>
                  <item id="p1" href="text/page1.xhtml" media-type="application/xhtml+xml"/>
                  <item id="p2" href="text/page2.xhtml" media-type="application/xhtml+xml"/>
                </manifest><spine><itemref idref="p1"/><itemref idref="p2"/></spine></package>
                """.trimIndent(),
            )
            add("OEBPS/text/page1.xhtml", """<html><body><img src="../images/1.jpg"/></body></html>""")
            add("OEBPS/text/page2.xhtml", """<html><body><img src="../images/2.jpg"/></body></html>""")
            add("OEBPS/images/1.jpg", "1")
            add("OEBPS/images/2.jpg", "2")
        }

        UniFile.fromFile(epub)!!.epubReader(context).use { reader ->
            reader.getImagesFromPages() shouldContainExactly listOf("OEBPS/images/1.jpg", "OEBPS/images/2.jpg")
            reader.getInputStream("OEBPS/images/2.jpg")!!.use { it.readBytes().decodeToString() } shouldBe "2"
        }
    }

    @Test
    fun `stores the cbz key in an owner-only file`() {
        val encrypted = CbzCrypto.encryptCbz("password")
        securityPreferences.cbzPassword().set(encrypted)
        CbzCrypto.getDecryptedPasswordCbz().decodeToString() shouldBe "password"

        val keyFile = DesktopDirs.dataDir.resolve("keys/cbzPw.key")
        keyFile.isFile shouldBe true
        keyFile.length() shouldBe 32L
        Files.getFileAttributeView(keyFile.toPath(), PosixFileAttributeView::class.java)?.let { view ->
            PosixFilePermissions.toString(view.readAttributes().permissions()) shouldBe "rw-------"
        }

        // A new key cannot decrypt the old ciphertext.
        val oldKey = keyFile.readBytes()
        CbzCrypto.deleteKeyCbz()
        keyFile.readBytes() shouldNotBe oldKey
        assertThrows<Exception> { CbzCrypto.getDecryptedPasswordCbz() }
    }

    private fun ArchiveReader.assertContent(
        encrypted: Boolean = false,
        expectDirectory: String? = null,
        extra: List<String> = emptyList(),
    ) {
        val entries = useEntries { entries -> entries.map { Triple(it.name, it.isFile, it.isEncrypted) }.toList() }
        val expected = listOfNotNull(expectDirectory?.let { Triple(it, false, false) }) +
            (pages.keys + extra).map { Triple(it, true, encrypted) }
        entries shouldContainExactly expected

        pages.forEach { (name, data) ->
            getInputStream(name)!!.use { it.readBytes() } shouldBe data
        }
        getInputStream("missing.jpg") shouldBe null

        // Reading every entry in one pass, like the Android reader's page loader.
        val read = useEntries { entries ->
            entries.filter { it.isFile }.associate { it.name to getInputStream(it.name)!!.use { s -> s.readBytes() } }
        }
        pages.forEach { (name, data) -> read[name] shouldBe data }
    }
}
