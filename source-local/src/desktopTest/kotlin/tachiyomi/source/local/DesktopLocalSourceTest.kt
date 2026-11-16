package tachiyomi.source.local

import android.app.Application
import android.net.Uri
import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.storage.FolderProvider
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * KMK: the local source (the same files as Android, `src/androidMain/kotlin`) on desktop: storage
 * directory from [StorageManager] over the compat `UniFile`, directory and CBZ chapters, ComicInfo.xml
 * through the compat `AndroidXmlReader`, cover extraction through core:archive's desktop reader.
 */
class DesktopLocalSourceTest {

    @TempDir
    lateinit var tempDir: File

    private fun localSource(): LocalSource {
        val storageDir = tempDir.resolve("storage").apply { mkdirs() }
        val context = Application(tempDir.resolve("data"))
        val storagePreferences = StoragePreferences(
            folderProvider = object : FolderProvider {
                override fun directory(): File = storageDir
                override fun path(): String = Uri.fromFile(storageDir).toString()
            },
            preferenceStore = DesktopPreferenceStore(tempDir.resolve("preferences.json")),
        )
        val fileSystem = LocalSourceFileSystem(StorageManager(context, storagePreferences))
        return LocalSource(
            context = context,
            fileSystem = fileSystem,
            coverManager = LocalCoverManager(context, fileSystem),
            allowHiddenFiles = { false },
        )
    }

    private fun createLibrary(): File {
        val mangaDir = tempDir.resolve("storage/local/Desktop Manga").apply { mkdirs() }

        // Chapter 1: CBZ with a page and ComicInfo.xml
        ZipOutputStream(mangaDir.resolve("Chapter 1.cbz").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.png"))
            zip.write(PNG)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("ComicInfo.xml"))
            zip.write(COMIC_INFO.toByteArray())
            zip.closeEntry()
        }

        // Chapter 2: plain directory of images
        mangaDir.resolve("Chapter 2").apply {
            mkdirs()
            resolve("001.png").writeBytes(PNG)
        }

        // Not a chapter
        mangaDir.resolve("notes.txt").writeText("ignored")
        return mangaDir
    }

    @Test
    fun `lists manga, reads details and chapters, extracts the cover`(): Unit = runBlocking {
        val mangaDir = createLibrary()
        val source = localSource()

        val page = source.getPopularManga(1)
        page.mangas.map { it.title } shouldContainExactly listOf("Desktop Manga")

        val manga = page.mangas.single()
        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)

        // ComicInfo.xml copied to the manga folder and used for the details
        update.manga.title shouldBe "Desktop Series"
        update.manga.author shouldBe "Jane Writer"
        mangaDir.resolve("ComicInfo.xml").isFile shouldBe true

        // Chapters: CBZ name from its ComicInfo.xml; sorted by name, descending (natural order)
        update.chapters.map { it.name } shouldContainExactly listOf("The First", "Chapter 2")
        update.chapters.map { it.url } shouldContainExactly
            listOf("Desktop Manga/Chapter 1.cbz", "Desktop Manga/Chapter 2")

        // Cover copied from the first chapter
        val cover = mangaDir.resolve("cover.jpg")
        cover.isFile shouldBe true
        cover.readBytes() shouldBe PNG
        update.manga.thumbnail_url.shouldNotBeNull() shouldEndWith "cover.jpg"
    }

    @Test
    fun `chapter formats`() {
        createLibrary()
        val source = localSource()

        val cbz = SChapter.create().apply { url = "Desktop Manga/Chapter 1.cbz" }
        val dir = SChapter.create().apply { url = "Desktop Manga/Chapter 2" }

        (source.getFormat(cbz) is Format.Archive) shouldBe true
        (source.getFormat(dir) is Format.Directory) shouldBe true
    }

    companion object {
        private val PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(),
        )

        private val COMIC_INFO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <Title>The First</Title>
              <Series>Desktop Series</Series>
              <Number>1</Number>
              <Writer>Jane Writer</Writer>
            </ComicInfo>
        """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun registerSerializers() {
            Injekt.addSingleton(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
            Injekt.addSingleton(
                XML {
                    defaultPolicy {
                        ignoreUnknownChildren()
                    }
                    autoPolymorphic = true
                    xmlDeclMode = XmlDeclMode.Charset
                    indent = 2
                    xmlVersion = XmlVersion.XML10
                },
            )
        }
    }
}
