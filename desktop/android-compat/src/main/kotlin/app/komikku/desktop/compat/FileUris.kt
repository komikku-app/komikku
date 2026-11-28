package app.komikku.desktop.compat

import android.net.Uri
import java.io.File

/**
 * KMK: `file:` URIs on every desktop OS. On Android (and macOS / Linux) a file URI's path is the file's absolute path.
 * A Windows path (`C:\Users\x`) is written `/C:/Users/x`, like `File.toURI`, and the slash before the drive is removed
 * again when the URI is read back; written as is, the backslashes were percent-encoded and the whole path parsed as
 * the URI's authority, leaving no path (no storage folder, no automatic backup on Windows). [separator] is
 * `File.separatorChar`, a parameter so the Windows form is tested on every OS.
 */
object FileUris {

    private val DRIVE_PATH = Regex("^/[A-Za-z]:(/.*)?$")
    private val DRIVE = Regex("^[A-Za-z]:\\\\.*")

    /** The decoded path of the `file:` URI of [absolutePath]. */
    fun uriPath(absolutePath: String, separator: Char = File.separatorChar): String {
        if (separator == '/') return absolutePath
        val path = absolutePath.replace(separator, '/')
        return if (path.startsWith('/')) path else "/$path"
    }

    /**
     * The file path [uri] names, or `null` when it is not a `file:` URI. On Windows this also reads the form earlier
     * versions stored (`file://C%3A%5CUsers%5Cx`, the path in the authority), so a storage folder chosen then is kept.
     */
    fun filePath(uri: Uri, separator: Char = File.separatorChar): String? {
        if (uri.scheme != SCHEME) return null
        val path = uri.path
        if (separator == '/') return path
        if (path.isNullOrEmpty()) return uri.authority?.takeIf { DRIVE.matches(it) }
        return if (DRIVE_PATH.matches(path)) path.substring(1) else path
    }

    /** The file [uri] names, or `null` when it is not a `file:` URI. */
    fun file(uri: Uri): File? = filePath(uri)?.let(::File)

    /** The file the URI string [uri] names (a stored storage location), or `null` when it is not a `file:` URI. */
    fun file(uri: String): File? = file(Uri.parse(uri))

    private const val SCHEME = "file"
}
