package android.content

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executor

/**
 * android-compat: `android.content.Context`. The desktop implementation behind
 * `android.app.Application` maps the app directories onto the desktop data dir.
 */
abstract class Context {

    abstract val applicationContext: Context
    abstract val packageName: String
    abstract val dataDir: File
    abstract val filesDir: File
    abstract val cacheDir: File
    abstract val externalCacheDir: File?
    abstract val contentResolver: ContentResolver
    abstract val resources: Resources
    abstract val classLoader: ClassLoader

    open val externalCacheDirs: Array<File?>
        get() = arrayOf(externalCacheDir)

    open val mainLooper: Looper
        get() = Looper.getMainLooper()

    open val mainExecutor: Executor
        get() = Executor { Handler(mainLooper).post(it) }

    /** The package name and data folder (shared preferences live in `preferences/` there, not `shared_prefs/`). */
    open val applicationInfo: ApplicationInfo
        get() = ApplicationInfo().also {
            it.packageName = packageName
            it.dataDir = dataDir.absolutePath
        }

    abstract fun getExternalFilesDir(type: String?): File?

    open fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(getExternalFilesDir(type))

    abstract fun getDir(name: String, mode: Int): File

    abstract fun getDatabasePath(name: String): File

    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences

    /** No Android system services exist on desktop; always `null`. */
    open fun getSystemService(name: String): Any? = null

    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_APPEND = 0x8000
        const val MODE_MULTI_PROCESS = 0x0004
    }
}
