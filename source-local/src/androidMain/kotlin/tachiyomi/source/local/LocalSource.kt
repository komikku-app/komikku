package tachiyomi.source.local

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import eu.kanade.tachiyomi.util.storage.EpubFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import rx.Observable
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.copyFromComicInfo
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.metadata.fillChapterMetadata
import tachiyomi.source.local.metadata.fillMangaMetadata
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.days
import com.github.junrar.Archive as JunrarArchive
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
    // SY -->
    private val allowHiddenFiles: () -> Boolean,
    // SY <--
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    private val POPULAR_FILTERS = FilterList(OrderBy.Popular(context))
    private val LATEST_FILTERS = FilterList(OrderBy.Latest(context))

    override val name: String = context.getString(R.string.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val baseDirsFiles = fileSystem.getFilesInBaseDirectories()
        val lastModifiedLimit by lazy { if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L }
        // SY -->
        val allowLocalSourceHiddenFolders = allowHiddenFiles()
        // SY <--

        var mangaDirs = baseDirsFiles
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && /* SY --> */ (!it.name.startsWith('.') || allowLocalSourceHiddenFolders) /* SY <-- */ }
            .distinctBy { it.name }
            .filter { // Filter by query or last modified
                if (lastModifiedLimit == 0L) {
                    it.name.contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Popular -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    } else {
                        mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                    }
                }
                is OrderBy.Latest -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedBy(File::lastModified)
                    } else {
                        mangaDirs.sortedByDescending(File::lastModified)
                    }
                }

                else -> {
                    /* Do nothing */
                }
            }
        }

        // Transform mangaDirs to list of SManga
        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                coverManager.find(mangaDir.name)
                    ?.takeIf(File::exists)
                    ?.let { thumbnail_url = it.absolutePath }
            }
        }

        // Fetch chapters of all the manga
        mangas.forEach { manga ->
            runBlocking {
                val chapters = getChapterList(manga)
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last()
                    val format = getFormat(chapter)

                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillMangaMetadata(manga)
                        }
                    }

                    // Copy the cover from the first chapter found if not available
                    if (manga.thumbnail_url == null) {
                        updateCover(chapter, manga)
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    // SY -->
    fun updateMangaInfo(manga: SManga) {
        val directory = fileSystem.getFilesInBaseDirectories().map { File(it, manga.url) }.find {
            it.exists()
        } ?: return
        val existingFileName = directory.listFiles()?.find { it.extension == "json" }?.name
        val file = File(directory, existingFileName ?: "info.json")
        file.outputStream().use {
            json.encodeToStream(manga.toJson(), it)
        }
    }

    private fun SManga.toJson(): MangaDetails {
        return MangaDetails(title, author, artist, description, genre?.split(", "), status)
    }
    // SY <--

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        coverManager.find(manga.url)?.let {
            manga.thumbnail_url = it.absolutePath
        }

        // Augment manga details based on metadata files
        try {
            val mangaDirFiles = fileSystem.getFilesInMangaDirectory(manga.url).toList()

            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val noXmlFile = mangaDirFiles
                .firstOrNull { it.name == ".noxml" }
            val legacyJsonDetailsFile = mangaDirFiles
                .firstOrNull { it.extension == "json" }
            // SY -->
            val comicInfoArchiveFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_ARCHIVE }
            // SY <--

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    noXmlFile?.delete()
                    setMangaDetailsFromComicInfoFile(comicInfoFile.inputStream(), manga)
                }
                // SY -->
                comicInfoArchiveFile != null -> {
                    val comicInfoArchive = ZipFile(comicInfoArchiveFile)
                    noXmlFile?.delete()

                    if (CbzCrypto.checkCbzPassword(comicInfoArchive, CbzCrypto.getDecryptedPasswordCbz())) {
                        comicInfoArchive.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                        val comicInfoEntry = comicInfoArchive.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }
                        setMangaDetailsFromComicInfoFile(comicInfoArchive.getInputStream(comicInfoEntry), manga)
                    }
                }
                // SY <--

                // TODO: automatically convert these to ComicInfo.xml
                legacyJsonDetailsFile != null -> {
                    json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.inputStream()).run {
                        title?.let { manga.title = it }
                        author?.let { manga.author = it }
                        artist?.let { manga.artist = it }
                        description?.let { manga.description = it }
                        genre?.let { manga.genre = it.joinToString() }
                        status?.let { manga.status = it }
                    }
                }

                // Copy ComicInfo.xml from chapter archive to top level if found
                noXmlFile == null -> {
                    val chapterArchives = mangaDirFiles
                        .filter(Archive::isSupported)
                        .toList()

                    val mangaDir = fileSystem.getMangaDirectory(manga.url)
                    val folderPath = mangaDir?.absolutePath

                    val copiedFile = copyComicInfoFileFromArchive(chapterArchives, folderPath)
                    // SY -->
                    if (copiedFile != null && copiedFile.name != COMIC_INFO_ARCHIVE) {
                        setMangaDetailsFromComicInfoFile(copiedFile.inputStream(), manga)
                    } else if (copiedFile != null && copiedFile.name == COMIC_INFO_ARCHIVE) {
                        val comicInfoArchive = ZipFile(copiedFile)
                        comicInfoArchive.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                        val comicInfoEntry = comicInfoArchive.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }

                        setMangaDetailsFromComicInfoFile(comicInfoArchive.getInputStream(comicInfoEntry), manga)
                    } // SY <--
                    else {
                        // Avoid re-scanning
                        File("$folderPath/.noxml").createNewFile()
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error setting manga details from local metadata for ${manga.title}" }
        }

        return@withIOContext manga
    }

    private fun copyComicInfoFileFromArchive(chapterArchives: List<File>, folderPath: String?): File? {
        for (chapter in chapterArchives) {
            when (Format.valueOf(chapter)) {
                is Format.Zip -> {
                    ZipFile(chapter).use { zip: ZipFile ->
                        // SY -->
                        if (zip.isEncrypted && !CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())
                        ) {
                            return null
                        } else if (zip.isEncrypted && CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())
                        ) {
                            zip.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                        }
                        zip.getFileHeader(COMIC_INFO_FILE)?.let { comicInfoFile ->
                            // SY <--
                            zip.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(chapter).use { rar ->
                        rar.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }?.let { comicInfoFile ->
                            rar.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    private fun copyComicInfoFile(comicInfoFileStream: InputStream, folderPath: String?): File {
        // SY -->
        if (
            CbzCrypto.getPasswordProtectDlPref() &&
            CbzCrypto.isPasswordSet()
        ) {
            val zipParameters = ZipParameters()
            CbzCrypto.setZipParametersEncrypted(zipParameters)
            zipParameters.fileNameInZip = COMIC_INFO_FILE

            val zipEncrypted = ZipFile("$folderPath/$COMIC_INFO_ARCHIVE")
            zipEncrypted.setPassword(CbzCrypto.getDecryptedPasswordCbz())
            zipEncrypted.addStream(comicInfoFileStream, zipParameters)
            return zipEncrypted.file
        } else {
            // SY <--
            return File("$folderPath/$COMIC_INFO_FILE").apply {
                outputStream().use { outputStream ->
                    comicInfoFileStream.use { it.copyTo(outputStream) }
                }
            }
        }
    }

    private fun setMangaDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        val comicInfo = AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }

        manga.copyFromComicInfo(comicInfo)
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fileSystem.getFilesInMangaDirectory(manga.url)
            // Only keep supported formats
            .filter { it.isDirectory || Archive.isSupported(it) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()
                    chapter_number = ChapterRecognition.parseChapterNumber(manga.title, this.name, this.chapter_number)

                    val format = Format.valueOf(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()
    }

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular(context))

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException("Unused")

    fun getFormat(chapter: SChapter): Format {
        try {
            return fileSystem.getBaseDirectories()
                .map { directory -> File(directory, chapter.url) }
                .find { chapterFile -> chapterFile.exists() }
                ?.let(Format.Companion::valueOf)
                ?: throw Exception(context.getString(R.string.chapter_not_found))
        } catch (e: Format.UnknownFormatException) {
            throw Exception(context.getString(R.string.local_invalid_format))
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                    entry?.let { coverManager.update(manga, it.inputStream()) }
                }
                is Format.Zip -> {
                    ZipFile(format.file).use { zip ->
                        // SY -->
                        if (zip.isEncrypted) zip.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                        val entry = zip.fileHeaders.toList()
                            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { zip.getInputStream(it) } }
                        // SY <--

                        entry?.let { coverManager.update(manga, zip.getInputStream(it)) }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(format.file).use { archive ->
                        val entry = archive.fileHeaders
                            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, archive.getInputStream(it)) }
                    }
                }
                is Format.Epub -> {
                    EpubFile(format.file).use { epub ->
                        val entry = epub.getImagesFromPages()
                            .firstOrNull()
                            ?.let { epub.getEntry(it) }

                        entry?.let { coverManager.update(manga, epub.getInputStream(it)) }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        // SY -->
        const val COMIC_INFO_ARCHIVE = "ComicInfo.cbm"
        // SY <--

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds
    }
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID
