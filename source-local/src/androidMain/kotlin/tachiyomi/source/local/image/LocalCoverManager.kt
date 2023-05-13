package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import eu.kanade.tachiyomi.util.storage.DiskUtil
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.File
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"
private const val COVER_ARCHIVE_NAME = "cover.cbi"

actual class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    actual fun find(mangaUrl: String): File? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with 'cover'
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull {
                ImageUtil.isImage(it.name) { it.inputStream() } || it.name == COVER_ARCHIVE_NAME
            }
    }

    actual fun update(
        manga: SManga,
        inputStream: InputStream,
        // SY -->
        encrypted: Boolean,
        // SY <--
    ): File? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        var targetFile = find(manga.url)
        if (targetFile == null) {
            // SY -->
            if (encrypted) {
                targetFile = File(directory.absolutePath, COVER_ARCHIVE_NAME)
            } else {
                targetFile = File(directory.absolutePath, DEFAULT_COVER_NAME)
                targetFile.createNewFile()
            }
            // SY <--
        }

        // It might not exist at this point
        targetFile.parentFile?.mkdirs()
        inputStream.use { input ->
            // SY -->
            if (encrypted) {
                val zip4j = ZipFile(targetFile)
                val zipParameters = ZipParameters()
                zip4j.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                CbzCrypto.setZipParametersEncrypted(zipParameters)
                zipParameters.fileNameInZip = DEFAULT_COVER_NAME
                zip4j.addStream(input, zipParameters)

                DiskUtil.createNoMediaFile(UniFile.fromFile(directory), context)

                manga.thumbnail_url = zip4j.file.absolutePath
                return zip4j.file
            } else {
                // SY <--
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                DiskUtil.createNoMediaFile(UniFile.fromFile(directory), context)
                manga.thumbnail_url = targetFile.absolutePath
                return targetFile
            }
        }
    }
}
