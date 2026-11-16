package android.app

import android.content.ContextWrapper
import app.komikku.desktop.compat.DesktopContext
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File

/**
 * android-compat: `android.app.Application`. On desktop it is a context over [dataDir]
 * (default: the desktop data dir, see `DesktopDirs`): files in `files/`, cache in `cache/`,
 * shared preferences in `preferences/` (the same files as the desktop `PreferenceStore`s).
 */
open class Application @JvmOverloads constructor(
    dataDir: File = DesktopDirs.dataDir,
    packageName: String = DEFAULT_PACKAGE_NAME,
) : ContextWrapper(null) {

    init {
        @Suppress("LeakingThis")
        attachBaseContext(DesktopContext(this, dataDir, packageName))
    }

    open fun onCreate() {}

    open fun onTerminate() {}

    companion object {
        const val DEFAULT_PACKAGE_NAME = "app.komikku"
    }
}
