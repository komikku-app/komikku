package app.komikku.sourcejs.store

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.IOException
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.memcpy
import platform.posix.open
import platform.posix.rename
import platform.posix.strerror
import platform.posix.unlink
import platform.posix.write
import kotlin.random.Random

/**
 * The few file operations of [IosJsSourceStore] on Foundation and POSIX. Paths are plain strings; failures are
 * [IOException]s with the system's reason.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object StoreFiles {

    private val fileManager get() = NSFileManager.defaultManager

    /** `<Application Support>/<name>` of the app's container (the directory itself may not exist yet). */
    fun applicationSupport(name: String): String {
        val base = (fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL)?.path
            ?: throw IllegalStateException("The app has no Application Support directory")
        return "$base/$name"
    }

    fun isFile(path: String): Boolean = memScoped {
        val isDirectory = alloc<kotlinx.cinterop.BooleanVar>()
        fileManager.fileExistsAtPath(path, isDirectory.ptr) && !isDirectory.value
    }

    fun exists(path: String): Boolean = fileManager.fileExistsAtPath(path)

    /** The size in bytes of the file at [path], `null` when it is missing or no file. */
    fun fileSize(path: String): Long? {
        if (!isFile(path)) return null
        return (fileManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue
    }

    /** The bytes of the file at [path], `null` when it is missing or cannot be read. */
    fun readBytes(path: String): ByteArray? {
        if (!isFile(path)) return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val size = data.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }

    fun createDirectories(path: String) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = error.ptr)) {
                throw IOException("Could not create $path: ${error.value?.localizedDescription}")
            }
        }
    }

    /**
     * Writes [bytes] to [path] atomically: a temporary file next to it is written and synced, then renamed over it
     * (`rename(2)` replaces the old file in one step), so a reader sees the old or the new file, never a part.
     */
    fun writeAtomically(path: String, bytes: ByteArray) {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty() && !exists(parent)) createDirectories(parent)
        val temp = "$path.${Random.nextLong().toULong().toString(16)}.tmp"
        try {
            writeSynced(temp, bytes)
            if (rename(temp, path) != 0) throw IOException("Could not replace $path: ${lastError()}")
        } catch (e: Throwable) {
            unlink(temp)
            throw e
        }
    }

    private fun writeSynced(path: String, bytes: ByteArray) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
        if (fd < 0) throw IOException("Could not write $path: ${lastError()}")
        try {
            var offset = 0
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    while (offset < bytes.size) {
                        val written = write(fd, pinned.addressOf(offset), (bytes.size - offset).convert())
                        if (written <= 0) throw IOException("Could not write $path: ${lastError()}")
                        offset += written.toInt()
                    }
                }
            }
            if (fsync(fd) != 0) throw IOException("Could not write $path: ${lastError()}")
        } finally {
            close(fd)
        }
    }

    /** Deletes the file or directory at [path] (with its contents); `false` when it was there and could not be. */
    fun delete(path: String): Boolean = !exists(path) || fileManager.removeItemAtPath(path, null)

    /** Renames [from] to [to] (replacing a file there); `false` when it failed. */
    fun move(from: String, to: String): Boolean = rename(from, to) == 0

    private fun lastError(): String = strerror(errno)?.toKString() ?: "error $errno"
}
