package mihon.core.archive

import java.io.File
import java.io.InputStream
import kotlin.concurrent.Volatile

/**
 * KMK: desktop counterpart of the Android (libarchive) `ArchiveInputStream`: walks the entries of
 * [file] with [getNextEntry] and reads the data of the current entry. Encrypted entries are read
 * with the CBZ password from [CbzCrypto] when [encrypted] is `true`.
 *
 * The Android constructor takes a memory-mapped buffer; desktop takes the archive file.
 */
class ArchiveInputStream(
    file: File,
    encrypted: Boolean,
) : InputStream() {
    private val lock = Any()

    @Volatile
    private var isClosed = false

    private val cursor = if (encrypted) {
        val password = CbzCrypto.getDecryptedPasswordCbz()
        val chars = password.decodeToString().toCharArray()
        password.fill(0)
        // The cursor owns the password and clears it when closed.
        DesktopArchiveCursor.open(file, chars)
    } else {
        DesktopArchiveCursor.open(file, null)
    }

    private var current: InputStream? = null
    private var hasEntry = false

    override fun read(): Int = currentStream()?.read() ?: -1

    override fun read(b: ByteArray, off: Int, len: Int): Int = currentStream()?.read(b, off, len) ?: -1

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
        }
        cursor.close()
    }

    fun getNextEntry(): ArchiveEntry? {
        current = null
        return cursor.next().also { hasEntry = it != null }
    }

    private fun currentStream(): InputStream? {
        if (!hasEntry) return null
        return current ?: cursor.openCurrent().also { current = it }
    }
}
