package eu.kanade.tachiyomi

import app.komikku.shared.reader.image.SkiaPageDecoder

/**
 * KMK: desktop counterpart of `app/src/main/java/eu/kanade/tachiyomi/AppInfo.kt` (follows `master` at `d126f2779f`).
 * Same API; the supported image types are the ones the desktop reader decodes ([SkiaPageDecoder.DECODED]), not every
 * type the desktop `ImageUtil` detects: it also detects AVIF, JPEG XL and HEIF to name them when they cannot be shown.
 *
 * Used by extensions.
 */
@Suppress("UNUSED")
object AppInfo {
    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = SkiaPageDecoder.DECODED.map { it.mime }
}
