package eu.kanade.tachiyomi.util.system

import android.content.Context

/*
 * KMK: desktop counterparts of the `Context` extensions (Android: app `ContextExtensions.kt`) that
 * borrowed `desktopCopy` files use. Neither installer exists on a desktop.
 */

/** Desktop: no MIUI package installer. */
val Context.hasMiuiPackageInstaller: Boolean get() = false

/** Desktop: no Shizuku. */
val Context.isShizukuInstalled: Boolean get() = false
