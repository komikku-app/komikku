package android.content

import android.content.res.Resources
import android.os.Looper
import java.io.File
import java.util.concurrent.Executor

/** android-compat: `android.content.ContextWrapper`, delegating everything to [baseContext]. */
open class ContextWrapper(base: Context?) : Context() {

    private var base: Context? = base

    open val baseContext: Context
        get() = checkNotNull(base) { "android-compat: base context not attached" }

    protected open fun attachBaseContext(base: Context) {
        check(this.base == null) { "Base context already set" }
        this.base = base
    }

    override val applicationContext: Context get() = baseContext.applicationContext
    override val packageName: String get() = baseContext.packageName
    override val dataDir: File get() = baseContext.dataDir
    override val filesDir: File get() = baseContext.filesDir
    override val cacheDir: File get() = baseContext.cacheDir
    override val externalCacheDir: File? get() = baseContext.externalCacheDir
    override val externalCacheDirs: Array<File?> get() = baseContext.externalCacheDirs
    override val contentResolver: ContentResolver get() = baseContext.contentResolver
    override val resources: Resources get() = baseContext.resources
    override val classLoader: ClassLoader get() = baseContext.classLoader
    override val mainLooper: Looper get() = baseContext.mainLooper
    override val mainExecutor: Executor get() = baseContext.mainExecutor

    override fun getExternalFilesDir(type: String?): File? = baseContext.getExternalFilesDir(type)

    override fun getExternalFilesDirs(type: String?): Array<File?> = baseContext.getExternalFilesDirs(type)

    override fun getDir(name: String, mode: Int): File = baseContext.getDir(name, mode)

    override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(name)

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences(name, mode)

    override fun getSystemService(name: String): Any? = baseContext.getSystemService(name)
}
