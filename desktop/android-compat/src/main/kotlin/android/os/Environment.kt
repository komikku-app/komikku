package android.os

import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File

/**
 * android-compat: `android.os.Environment`. "External storage" is the user's home directory and
 * is always mounted; the data directory is the desktop data dir.
 */
object Environment {

    const val MEDIA_UNKNOWN = "unknown"
    const val MEDIA_REMOVED = "removed"
    const val MEDIA_UNMOUNTED = "unmounted"
    const val MEDIA_CHECKING = "checking"
    const val MEDIA_NOFS = "nofs"
    const val MEDIA_MOUNTED = "mounted"
    const val MEDIA_MOUNTED_READ_ONLY = "mounted_ro"
    const val MEDIA_SHARED = "shared"
    const val MEDIA_BAD_REMOVAL = "bad_removal"
    const val MEDIA_UNMOUNTABLE = "unmountable"

    @JvmField val DIRECTORY_MUSIC: String = "Music"

    @JvmField val DIRECTORY_PODCASTS: String = "Podcasts"

    @JvmField val DIRECTORY_RINGTONES: String = "Ringtones"

    @JvmField val DIRECTORY_ALARMS: String = "Alarms"

    @JvmField val DIRECTORY_NOTIFICATIONS: String = "Notifications"

    @JvmField val DIRECTORY_PICTURES: String = "Pictures"

    @JvmField val DIRECTORY_MOVIES: String = "Movies"

    @JvmField val DIRECTORY_DOWNLOADS: String = "Downloads"

    @JvmField val DIRECTORY_DCIM: String = "DCIM"

    @JvmField val DIRECTORY_DOCUMENTS: String = "Documents"

    @JvmStatic
    fun getExternalStorageDirectory(): File = File(System.getProperty("user.home"))

    @JvmStatic
    fun getExternalStoragePublicDirectory(type: String): File = getExternalStorageDirectory().resolve(type)

    @JvmStatic
    fun getExternalStorageState(): String = MEDIA_MOUNTED

    @JvmStatic
    fun getExternalStorageState(path: File): String = if (path.exists()) MEDIA_MOUNTED else MEDIA_UNKNOWN

    @JvmStatic
    fun getDataDirectory(): File = DesktopDirs.dataDir

    @JvmStatic
    fun getDownloadCacheDirectory(): File = DesktopDirs.dataDir.resolve("cache")

    @JvmStatic
    fun isExternalStorageEmulated(): Boolean = false

    @JvmStatic
    fun isExternalStorageRemovable(): Boolean = false

    @JvmStatic
    fun isExternalStorageManager(): Boolean = true
}
