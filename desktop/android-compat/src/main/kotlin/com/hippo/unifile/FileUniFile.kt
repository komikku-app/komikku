package com.hippo.unifile

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/** [UniFile] over a `java.io.File`. */
internal class FileUniFile(
    parent: UniFile?,
    private var file: File,
) : UniFile(parent) {

    override fun createFile(displayName: String?): UniFile? {
        if (displayName.isNullOrEmpty()) return null
        val target = File(file, displayName)
        if (target.exists()) return if (target.isFile) FileUniFile(this, target) else null
        return try {
            if (target.createNewFile()) FileUniFile(this, target) else null
        } catch (_: IOException) {
            null
        }
    }

    override fun createDirectory(displayName: String?): UniFile? {
        if (displayName.isNullOrEmpty()) return null
        val target = File(file, displayName)
        if (target.isDirectory || target.mkdirs()) return FileUniFile(this, target)
        return null
    }

    override fun mkdir(): Boolean = file.isDirectory || file.mkdirs()

    override val uri: Uri get() = Uri.fromFile(file)

    override val name: String get() = file.name

    override val type: String?
        get() {
            if (file.isDirectory) return null
            val extension = file.name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

    override val filePath: String get() = file.path

    override val isDirectory: Boolean get() = file.isDirectory

    override val isFile: Boolean get() = file.isFile

    override fun lastModified(): Long = file.lastModified()

    override fun length(): Long = file.length()

    override fun canRead(): Boolean = file.canRead()

    override fun canWrite(): Boolean = file.canWrite()

    override fun delete(): Boolean = file.deleteRecursively()

    override fun exists(): Boolean = file.exists()

    override fun listFiles(): Array<UniFile>? =
        file.listFiles()?.map { FileUniFile(this, it) }?.toTypedArray()

    override fun listFiles(filter: FilenameFilter?): Array<UniFile>? {
        if (filter == null) return listFiles()
        val children = file.listFiles() ?: return null
        return children
            .filter { filter.accept(this, it.name) }
            .map { FileUniFile(this, it) }
            .toTypedArray()
    }

    override fun findFile(displayName: String?): UniFile? {
        if (displayName.isNullOrEmpty()) return null
        val child = File(file, displayName)
        return if (child.exists()) FileUniFile(this, child) else null
    }

    override fun renameTo(displayName: String?): Boolean {
        if (displayName.isNullOrEmpty()) return false
        val target = File(file.parentFile, displayName)
        if (!file.renameTo(target)) return false
        file = target
        return true
    }

    override fun openOutputStream(): OutputStream = FileOutputStream(file)

    override fun openOutputStream(append: Boolean): OutputStream = FileOutputStream(file, append)

    override fun openInputStream(): InputStream = FileInputStream(file)

    override fun createRandomAccessFile(mode: String?): UniRandomAccessFile =
        FileRandomAccessFile(RandomAccessFile(file, mode ?: "r"))

    override fun toString(): String = "FileUniFile($file)"
}
