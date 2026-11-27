package android.content.pm

/**
 * android-compat: `android.content.pm.ApplicationInfo`, the fields extensions read (TCB Scans looks for
 * `<dataDir>/shared_prefs` to migrate old preferences). Filled by `Context.getApplicationInfo()`.
 */
open class ApplicationInfo {

    @JvmField
    var packageName: String? = null

    @JvmField
    var dataDir: String? = null

    @JvmField
    var flags: Int = 0

    companion object {
        const val FLAG_DEBUGGABLE = 1 shl 1
    }
}
