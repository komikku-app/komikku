package app.komikku.desktop.compat

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import java.io.File

/**
 * The desktop [Context] implementation behind `android.app.Application`.
 *
 * Layout under [dataDirectory]: `files/`, `cache/` (also used as the external cache dir),
 * `files/<type>/` for `getExternalFilesDir(type)`, `databases/`, `app_<name>/` for `getDir`, and
 * `preferences/<name>.json` for shared preferences. Directories are created on access, like on
 * Android, until [stopCreatingFolders] (see [stopCreatingDataFolders]).
 */
internal class DesktopContext(
    private val application: Context,
    private val dataDirectory: File,
    override val packageName: String,
) : Context() {

    /** False once the data folder is being torn down: the getters then only return the paths. */
    @Volatile
    private var createsFolders = true

    override val applicationContext: Context get() = application
    override val dataDir: File get() = dataDirectory.ensureDir()
    override val filesDir: File get() = dataDirectory.resolve("files").ensureDir()
    override val cacheDir: File get() = dataDirectory.resolve("cache").ensureDir()
    override val externalCacheDir: File get() = cacheDir
    override val contentResolver: ContentResolver by lazy { ContentResolver(application) }
    override val resources: Resources get() = Resources.getSystem()
    override val classLoader: ClassLoader get() = DesktopContext::class.java.classLoader

    override fun getExternalFilesDir(type: String?): File =
        (if (type.isNullOrEmpty()) filesDir else filesDir.resolve(type)).ensureDir()

    override fun getDir(name: String, mode: Int): File = dataDirectory.resolve("app_$name").ensureDir()

    override fun getDatabasePath(name: String): File {
        val file = File(name)
        return if (file.isAbsolute) file else dataDirectory.resolve("databases").ensureDir().resolve(name)
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        DesktopPreferenceStores.sharedPreferences(name, dataDirectory.resolve(PREFERENCES_DIR))

    /** From now on the folder getters return their paths without creating the folders. */
    fun stopCreatingFolders() {
        createsFolders = false
    }

    private fun File.ensureDir(): File = apply { if (createsFolders && !isDirectory) mkdirs() }

    companion object {
        const val PREFERENCES_DIR = "preferences"
    }
}

/**
 * Desktop teardown only: from now on this application's folder getters (`cacheDir`, `filesDir`, `getDir`, …) return
 * their paths without creating the folders, so a late reader (a delayed index write, a cache) cannot re-create a
 * folder of the data directory while it is deleted. Irreversible for this instance; a new [Application] over the same
 * folder creates folders again. Not called in normal use, where folders are created on access like on Android.
 */
fun Application.stopCreatingDataFolders() {
    (baseContext as DesktopContext).stopCreatingFolders()
}
