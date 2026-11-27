package android.app

import android.content.ContextWrapper
import android.os.Bundle
import app.komikku.desktop.compat.DesktopContext
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

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

    private val lifecycleCallbacks = CopyOnWriteArrayList<ActivityLifecycleCallbacks>()

    /** Kept like on Android; never called, as the desktop app has no activities. */
    fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks += callback
    }

    fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks -= callback
    }

    /** android-compat: `Application.ActivityLifecycleCallbacks` (the methods every API level has). */
    interface ActivityLifecycleCallbacks {
        fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?)
        fun onActivityStarted(activity: Activity)
        fun onActivityResumed(activity: Activity)
        fun onActivityPaused(activity: Activity)
        fun onActivityStopped(activity: Activity)
        fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
        fun onActivityDestroyed(activity: Activity)
    }

    companion object {
        const val DEFAULT_PACKAGE_NAME = "app.komikku"
    }
}
