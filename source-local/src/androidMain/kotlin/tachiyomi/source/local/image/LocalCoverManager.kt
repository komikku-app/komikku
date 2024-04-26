package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import tachiyomi.core.common.storage.addStreamToZip
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"
private const val COVER_ARCHIVE_NAME = "cover.cbi"

actual class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    actual fun find(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with "cover"
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull {
                ImageUtil.isImage(it.name) { it.openInputStream() } || it.name == COVER_ARCHIVE_NAME
            }
    }

    actual fun update(
        manga: SManga,
        inputStream: InputStream,
        // SY -->
        encrypted: Boolean,
        // SY <--
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        var targetFile = find(manga.url)
        if (targetFile == null) {
            // SY -->
            targetFile = if (encrypted) {
                directory.createFile(COVER_ARCHIVE_NAME)
            } else {
                directory.createFile(DEFAULT_COVER_NAME)
            }
            // SY <--
        }

        targetFile!!

        inputStream.use { input ->
            // SY -->
            if (encrypted) {
                targetFile.addStreamToZip(inputStream, DEFAULT_COVER_NAME, CbzCrypto.getDecryptedPasswordCbz())
                DiskUtil.createNoMediaFile(directory, context)

                manga.thumbnail_url = targetFile.uri.toString()
                return targetFile
            } else {
                // SY <--
                targetFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
                DiskUtil.createNoMediaFile(directory, context)
                manga.thumbnail_url = targetFile.uri.toString()
                return targetFile
            }
        }
    }
}
