package tachiyomi.core.common.storage

import android.net.Uri
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File

/**
 * KMK: desktop counterpart of `AndroidStorageFolderProvider`: the default storage location
 * (downloads, local source, backups) is `storage/` in the desktop data dir (PLAN.md D7), so a
 * portable install (`komikku.dataDir`) keeps everything in one place.
 */
class DesktopStorageFolderProvider(
    private val baseDir: File = DesktopDirs.dataDir,
) : FolderProvider {

    /**
     * Created on first use: `StorageManager` only accepts an existing directory as the base, so a fresh
     * data dir would otherwise have no downloads / local source folder until the user picks one.
     */
    override fun directory(): File = File(baseDir, STORAGE_DIR).also { it.mkdirs() }

    /** `file:` URI of [directory], like the Android provider. */
    override fun path(): String = Uri.fromFile(directory()).toString()

    private companion object {
        const val STORAGE_DIR = "storage"
    }
}
