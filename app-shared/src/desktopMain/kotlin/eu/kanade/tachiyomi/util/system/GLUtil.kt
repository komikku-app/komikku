package eu.kanade.tachiyomi.util.system

/**
 * KMK: desktop counterpart of `GLUtil` (Android: `core/common/src/androidMain`), for the borrowed
 * `BasePreferences`. Skia on desktop has no fixed texture limit, so the safe default is used.
 */
object GLUtil {
    const val SAFE_TEXTURE_LIMIT = 2048
    val DEVICE_TEXTURE_LIMIT: Int get() = SAFE_TEXTURE_LIMIT
}
