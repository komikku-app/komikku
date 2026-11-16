package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.InputStream

// KMK --> `actual` of the shared expect class (src/jvmSharedMain); the desktop actual is in desktopMain.
actual class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    // KMK <--
    val size = pfd.statSize
    val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)

    // SY -->
    // KMK -->
    actual var encrypted: Boolean = false
        private set
    actual var wrongPassword: Boolean? = null
        private set
    actual val archiveHashCode = pfd.hashCode()
    // KMK <--

    init {
        checkEncryptionStatus()
    }
    // SY <--

    // KMK -->
    actual inline fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = ArchiveInputStream(
        // KMK <--
        address,
        size,
        // SY -->
        encrypted,
        // SY <--
    ).use { block(generateSequence { it.getNextEntry() }) }

    // KMK -->
    actual fun getInputStream(entryName: String): InputStream? {
        // KMK <--
        val archive = ArchiveInputStream(address, size, /* SY --> */ encrypted /* SY <-- */)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    return archive
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        return null
    }

    // SY -->
    private fun checkEncryptionStatus() {
        val archive = ArchiveInputStream(address, size, false)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.isEncrypted) {
                    encrypted = true
                    isPasswordIncorrect(entry.name)
                    break
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
    }

    private fun isPasswordIncorrect(entryName: String) {
        try {
            getInputStream(entryName).use { stream ->
                stream!!.read()
            }
        } catch (e: ArchiveException) {
            if (e.message == "Incorrect passphrase") {
                wrongPassword = true
                return
            }
            throw e
        }
        wrongPassword = false
    }
    // SY <--

    // KMK -->
    actual override fun close() {
        // KMK <--
        Os.munmap(address, size)
    }
}
