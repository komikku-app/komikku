package app.komikku.desktop.compat

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
 * Android.
 */
internal class DesktopContext(
    private val application: Context,
    private val dataDirectory: File,
    override val packageName: String,
) : Context() {

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

    private fun File.ensureDir(): File = apply { if (!isDirectory) mkdirs() }

    companion object {
        const val PREFERENCES_DIR = "preferences"
    }
}
