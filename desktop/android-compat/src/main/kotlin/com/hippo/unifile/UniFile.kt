package com.hippo.unifile

import android.content.Context
import android.net.Uri
import app.komikku.desktop.compat.unsupported
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * android-compat: `com.hippo.unifile.UniFile` (the komikku-app fork's API) for desktop, where every
 * file is a plain `java.io.File` ([FileUniFile], the equivalent of UniFile's raw file). Asset,
 * resource, media and document-tree files do not exist on desktop.
 */
abstract class UniFile internal constructor(
    private val parent: UniFile?,
) {

    /** Creates (or returns the existing) file [displayName] in this directory, or `null`. */
    abstract fun createFile(displayName: String?): UniFile?

    /** Creates (or returns the existing) directory [displayName] in this directory, or `null`. */
    abstract fun createDirectory(displayName: String?): UniFile?

    /** Creates this directory and missing parents; `true` if it exists afterwards. */
    open fun mkdir(): Boolean? = null

    abstract val uri: Uri

    abstract val name: String?

    abstract val type: String?

    abstract val filePath: String?

    /** The directory this file was obtained from (`null` for files created with `fromFile`/`fromUri`). */
    open val parentFile: UniFile?
        get() = parent

    abstract val isDirectory: Boolean

    abstract val isFile: Boolean

    abstract fun lastModified(): Long

    abstract fun length(): Long

    abstract fun canRead(): Boolean

    abstract fun canWrite(): Boolean

    abstract fun delete(): Boolean

    abstract fun exists(): Boolean

    abstract fun listFiles(): Array<UniFile>?

    abstract fun listFiles(filter: FilenameFilter?): Array<UniFile>?

    abstract fun findFile(displayName: String?): UniFile?

    abstract fun renameTo(displayName: String?): Boolean

    @Throws(IOException::class)
    abstract fun openOutputStream(): OutputStream

    @Throws(IOException::class)
    abstract fun openOutputStream(append: Boolean): OutputStream

    @Throws(IOException::class)
    abstract fun openInputStream(): InputStream

    @Throws(IOException::class)
    abstract fun createRandomAccessFile(mode: String?): UniRandomAccessFile

    companion object {
        private val uriHandlers = CopyOnWriteArrayList<UriHandler>()

        @JvmStatic
        fun addUriHandler(handler: UriHandler) {
            uriHandlers.addIfAbsent(handler)
        }

        @JvmStatic
        fun removeUriHandler(handler: UriHandler) {
            uriHandlers.remove(handler)
        }

        @JvmStatic
        fun fromFile(file: File?): UniFile? = file?.let { FileUniFile(null, it) }

        @JvmStatic
        fun fromResource(context: Context?, id: Int): UniFile? = unsupported("UniFile.fromResource")

        /** `file:` URIs map to files; other URIs go through the registered [UriHandler]s. */
        @JvmStatic
        fun fromUri(context: Context?, uri: Uri?): UniFile? {
            if (uri == null) return null
            if (isFileUri(uri)) return uri.path?.let { FileUniFile(null, File(it)) }
            if (context == null) return null
            return uriHandlers.firstNotNullOfOrNull { it.fromUri(context, uri) }
        }

        @JvmStatic
        fun isFileUri(uri: Uri?): Boolean = uri != null && uri.scheme == "file"

        @JvmStatic
        fun isDocumentUri(context: Context?, uri: Uri?): Boolean = false

        @JvmStatic
        fun isTreeDocumentUri(context: Context?, uri: Uri?): Boolean = false

        @JvmStatic
        fun isAssetUri(uri: Uri?): Boolean = false

        @JvmStatic
        fun isMediaUri(uri: Uri?): Boolean = false
    }
}
