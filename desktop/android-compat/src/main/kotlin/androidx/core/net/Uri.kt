package androidx.core.net

import android.net.Uri
import app.komikku.desktop.compat.FileUris
import java.io.File

/** android-compat: core-ktx `androidx.core.net.UriKt`. */
inline fun String.toUri(): Uri = Uri.parse(this)

/** Creates a `file:` [Uri] for this file. */
inline fun File.toUri(): Uri = Uri.fromFile(this)

/** The [File] of a `file:` [Uri]; throws [IllegalArgumentException] for other schemes. */
fun Uri.toFile(): File {
    require(scheme == "file") { "Uri lacks 'file' scheme: $this" }
    return File(requireNotNull(FileUris.filePath(this)) { "Uri path is null: $this" })
}
