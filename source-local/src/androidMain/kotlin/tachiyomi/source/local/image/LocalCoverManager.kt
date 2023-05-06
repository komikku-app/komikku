package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

// SY -->
private const val NO_COVER_FILE = ".nocover"
private const val CACHE_COVER_INTERNAL = ".cacheCoverInternal"
private const val LOCAL_CACHE_DIR = "covers/local"
// SY <--

actual class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,

    // SY -->
    private val coverCacheDir: File? = context.getExternalFilesDir(LOCAL_CACHE_DIR),
    private val securityPreferences: SecurityPreferences = Injekt.get(),
    // SY <--

) {

    actual fun find(mangaUrl: String): File? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with 'cover'
            // --> SY
            .filter { (it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true)) || it.name == NO_COVER_FILE || it.name == CACHE_COVER_INTERNAL }
            // Get the first actual image
            .firstOrNull {
                if (it.name != NO_COVER_FILE && it.name != CACHE_COVER_INTERNAL) {
                    ImageUtil.isImage(it.name) { it.inputStream() }
                } else if (it.name == NO_COVER_FILE) {
                    true
                } else if (it.name == CACHE_COVER_INTERNAL) {
                    return File("$coverCacheDir/${it.parentFile?.name}/$DEFAULT_COVER_NAME")
                } else {
                    false
                }
                // SY <--
            }
    }

    actual fun update(
        manga: SManga,
        inputStream: InputStream,
    ): File? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        var targetFile = find(manga.url)
        if (targetFile == null) {
            // SY -->
            targetFile = when (securityPreferences.localCoverLocation().get()) {
                SecurityPreferences.CoverCacheLocation.INTERNAL -> File(directory.absolutePath, CACHE_COVER_INTERNAL)
                SecurityPreferences.CoverCacheLocation.NEVER -> File(directory.absolutePath, NO_COVER_FILE)
                SecurityPreferences.CoverCacheLocation.IN_MANGA_DIRECTORY -> File(directory.absolutePath, DEFAULT_COVER_NAME)
            }
            if (targetFile.parentFile?.parentFile?.name != "local") targetFile.parentFile?.mkdirs()
            targetFile.createNewFile()
        }

        if (targetFile.name == NO_COVER_FILE) return null
        if (securityPreferences.localCoverLocation().get() == SecurityPreferences.CoverCacheLocation.IN_MANGA_DIRECTORY) {
            // SY <--
            // It might not exist at this point
            targetFile.parentFile?.mkdirs()
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                DiskUtil.createNoMediaFile(UniFile.fromFile(directory), context)
                manga.thumbnail_url = targetFile.absolutePath
                return targetFile
                // SY -->
            }
        } else if (securityPreferences.localCoverLocation().get() == SecurityPreferences.CoverCacheLocation.INTERNAL) {
            // It might not exist at this point
            targetFile.parentFile?.mkdirs()
            val path = "$coverCacheDir/${targetFile.parentFile?.name}/$DEFAULT_COVER_NAME"
            val outputFile = File(path)

            outputFile.parentFile?.mkdirs()
            outputFile.createNewFile()

            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            manga.thumbnail_url = outputFile.absolutePath
            return outputFile
        } else {
            return null
        }
        // SY <--
    }
}
