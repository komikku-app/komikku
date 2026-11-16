package android.content

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * android-compat: `android.content.ContentResolver`. Only `file:` URIs exist on desktop; content
 * providers are not supported.
 */
open class ContentResolver(
    @Suppress("UNUSED_PARAMETER") context: Context?,
) {

    @Throws(FileNotFoundException::class)
    fun openInputStream(uri: Uri): InputStream? = FileInputStream(uri.requireFile())

    @Throws(FileNotFoundException::class)
    fun openOutputStream(uri: Uri): OutputStream? = openOutputStream(uri, "w")

    /** [mode] as on Android: "w" / "wt" truncate, "wa" appends, "rw" / "rwt" are accepted too. */
    @Throws(FileNotFoundException::class)
    fun openOutputStream(uri: Uri, mode: String): OutputStream? =
        FileOutputStream(uri.requireFile(), "a" in mode)

    fun getType(uri: Uri): String? {
        if (uri.scheme != SCHEME_FILE) return null
        val name = uri.lastPathSegment ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    }

    private fun Uri.requireFile(): File {
        if (scheme != SCHEME_FILE) throw FileNotFoundException("android-compat: only file URIs are supported, got $this")
        return File(path ?: throw FileNotFoundException("android-compat: no path in $this"))
    }

    companion object {
        const val SCHEME_CONTENT = "content"
        const val SCHEME_ANDROID_RESOURCE = "android.resource"
        const val SCHEME_FILE = "file"
    }
}
