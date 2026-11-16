package mihon.core.archive

import com.github.junrar.Archive
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.compressors.CompressorException
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import com.github.junrar.rarfile.FileHeader as RarFileHeader
import org.apache.commons.compress.archivers.ArchiveInputStream as CompressArchiveInputStream

/**
 * KMK: sequential access to the entries of an archive on desktop, the equivalent of libarchive's
 * `readNextHeader` / `readData` loop on Android. One cursor = one set of open file handles, so
 * cursors can be used from different threads at the same time.
 *
 * The format is detected from the file's content, like libarchive:
 * - zip (cbz, epub): zip4j, central directory listing, AES and ZipCrypto passwords;
 * - RAR 4 (cbr): junrar (RAR 5 is not supported by junrar);
 * - 7z (cb7): commons-compress + xz, AES passwords;
 * - anything commons-compress detects (tar / cbt, cpio, ar, arj, …), also inside gzip / bzip2 /
 *   xz / … compressors: streamed.
 */
internal abstract class DesktopArchiveCursor : Closeable {

    /** Moves to the next entry; `null` at the end. */
    abstract fun next(): ArchiveEntry?

    /** Data of the current entry. Valid until [next] or [close]; closing it does not close the cursor. */
    abstract fun openCurrent(): InputStream

    companion object {
        /**
         * Opens [file]. [password] is used for encrypted entries; without it, entries can be listed
         * but encrypted data cannot be read. The cursor takes ownership of [password] (the
         * libraries read it lazily) and clears it on [close] or when opening fails.
         */
        fun open(file: File, password: CharArray?): DesktopArchiveCursor {
            try {
                val magic = file.inputStream().use { input ->
                    ByteArray(MAGIC_LENGTH).let { it.copyOf(input.readNBytes(it, 0, it.size)) }
                }
                val cursor = when {
                    magic.startsWith(ZIP_LOCAL) || magic.startsWith(ZIP_EMPTY) || magic.startsWith(ZIP_SPANNED) ->
                        ZipCursor(file, password)
                    magic.startsWith(RAR) -> RarCursor(file)
                    magic.startsWith(SEVEN_Z) -> SevenZCursor(file, password)
                    else -> StreamCursor(file)
                }
                return if (password == null) cursor else PasswordClearingCursor(cursor, password)
            } catch (e: Throwable) {
                password?.fill('\u0000')
                throw e
            }
        }

        private const val MAGIC_LENGTH = 8
        private val ZIP_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val ZIP_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        private val ZIP_SPANNED = byteArrayOf(0x50, 0x4B, 0x07, 0x08)
        private val RAR = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
        private val SEVEN_Z = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}

/** Clears the password once the wrapped cursor is closed. */
private class PasswordClearingCursor(
    private val cursor: DesktopArchiveCursor,
    private val password: CharArray,
) : DesktopArchiveCursor() {
    override fun next(): ArchiveEntry? = cursor.next()
    override fun openCurrent(): InputStream = cursor.openCurrent()
    override fun close() {
        try {
            cursor.close()
        } finally {
            password.fill('\u0000')
        }
    }
}

/** Zip through zip4j: entries from the central directory, data opened per entry. */
private class ZipCursor(file: File, password: CharArray?) : DesktopArchiveCursor() {

    private val zip = ZipFile(file, password?.takeIf { it.isNotEmpty() })
    private val headers: Iterator<FileHeader> = zip.fileHeaders.iterator()
    private var current: FileHeader? = null
    private var currentStream: InputStream? = null

    override fun next(): ArchiveEntry? {
        closeCurrentStream()
        val header = if (headers.hasNext()) headers.next() else null
        current = header
        return header?.let { ArchiveEntry(it.fileName, !it.isDirectory, it.isEncrypted) }
    }

    override fun openCurrent(): InputStream {
        currentStream?.let { return it }
        val header = checkNotNull(current) { "No current entry" }
        return zip.getInputStream(header).also { currentStream = it }
    }

    private fun closeCurrentStream() {
        currentStream?.close()
        currentStream = null
    }

    override fun close() {
        closeCurrentStream()
        zip.close()
    }
}

/** RAR 4 through junrar; an entry's data is extracted to memory when first read. */
private class RarCursor(file: File) : DesktopArchiveCursor() {

    private val archive = try {
        Archive(file)
    } catch (e: Exception) {
        throw e as? IOException ?: IOException("Cannot open RAR archive: ${e.message}", e)
    }
    private val headers: Iterator<RarFileHeader> = archive.fileHeaders.iterator()
    private var current: RarFileHeader? = null
    private var currentData: InputStream? = null

    override fun next(): ArchiveEntry? {
        currentData = null
        val header = if (headers.hasNext()) headers.next() else null
        current = header
        return header?.let { ArchiveEntry(it.fileName, !it.isDirectory, it.isEncrypted) }
    }

    override fun openCurrent(): InputStream {
        currentData?.let { return it }
        val header = checkNotNull(current) { "No current entry" }
        if (header.isEncrypted) throw IOException("Encrypted RAR entries are not supported: ${header.fileName}")
        val bytes = ByteArrayOutputStream(header.fullUnpackSize.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        try {
            archive.extractFile(header, bytes)
        } catch (e: Exception) {
            throw e as? IOException ?: IOException("Cannot extract ${header.fileName}: ${e.message}", e)
        }
        return ByteArrayInputStream(bytes.toByteArray()).also { currentData = it }
    }

    override fun close() {
        archive.close()
    }
}

/** 7z through commons-compress; encrypted entries are read with [password]. */
private class SevenZCursor(file: File, password: CharArray?) : DesktopArchiveCursor() {

    private val sevenZ = SevenZFile.builder()
        .setFile(file)
        .apply { if (password != null && password.isNotEmpty()) setPassword(password) }
        .get()

    private var currentEncrypted = false

    override fun next(): ArchiveEntry? {
        val entry = sevenZ.nextEntry ?: return null
        currentEncrypted = entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true
        return ArchiveEntry(entry.name, !entry.isDirectory, currentEncrypted)
    }

    // 7z has no password check value: with a wrong password, decoding an encrypted entry fails.
    override fun openCurrent(): InputStream = object : InputStream() {
        override fun read(): Int = guard { sevenZ.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { sevenZ.read(b, off, len) }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        if (currentEncrypted) throw WrongPasswordException(e) else throw e
    }

    override fun close() {
        sevenZ.close()
    }
}

/** Formats commons-compress detects in a stream (tar, cpio, ar, …), optionally compressed. */
private class StreamCursor(file: File) : DesktopArchiveCursor() {

    private val input: CompressArchiveInputStream<*> = openStream(file)

    // None of these formats is encrypted (encrypted zip / 7z / RAR have their own cursors).
    override fun next(): ArchiveEntry? {
        val entry = input.nextEntry ?: return null
        return ArchiveEntry(entry.name, !entry.isDirectory, isEncrypted = false)
    }

    /** The archive stream positioned at the current entry; closing it leaves the cursor open. */
    override fun openCurrent(): InputStream = object : FilterInputStream(input) {
        override fun close() {}
    }

    override fun close() {
        input.close()
    }

    private companion object {
        fun openStream(file: File): CompressArchiveInputStream<*> {
            var stream: InputStream = BufferedInputStream(file.inputStream())
            try {
                val compressor = try {
                    CompressorStreamFactory.detect(stream)
                } catch (_: CompressorException) {
                    null
                }
                if (compressor != null) {
                    stream = BufferedInputStream(CompressorStreamFactory().createCompressorInputStream(compressor, stream))
                }
                val format = ArchiveStreamFactory.detect(stream)
                return ArchiveStreamFactory().createArchiveInputStream(format, stream)
            } catch (e: Exception) {
                stream.close()
                throw e as? IOException ?: IOException("Unsupported archive format: ${file.name}", e)
            }
        }
    }
}

/** A password-protected entry could not be decrypted with the given password. */
internal class WrongPasswordException(cause: Throwable? = null) : IOException("Incorrect passphrase", cause)

/** Whether this error means the archive password is wrong (libarchive's "Incorrect passphrase"). */
internal fun IOException.isWrongPassword(): Boolean =
    this is WrongPasswordException || (this is ZipException && type == ZipException.Type.WRONG_PASSWORD)
