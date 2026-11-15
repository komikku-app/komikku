package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo

// KMK -->
/**
 * Some Android distributions protect package enumeration with an additional runtime permission.
 * Detect it at runtime instead of tying an OEM permission to an Android API level.
 */
internal object InstalledAppsPermission {
    const val NAME = "com.android.permission.GET_INSTALLED_APPS"

    @Suppress("DEPRECATION")
    fun isRuntimePermission(context: Context): Boolean {
        val permission = try {
            context.packageManager.getPermissionInfo(NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
    }
}
// KMK <--
