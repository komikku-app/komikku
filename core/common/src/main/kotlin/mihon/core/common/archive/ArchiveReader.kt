package mihon.core.common.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.hippo.unifile.UniFile
import me.zhanghai.android.libarchive.ArchiveException
import tachiyomi.core.common.storage.openFileDescriptor
import java.io.Closeable
import java.io.InputStream

class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    val size = pfd.statSize
    val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)

    // SY -->
    var encrypted: Boolean = false
        private set
    var wrongPassword: Boolean? = null
        private set
    val archiveHashCode = pfd.hashCode()

    init {
        checkEncryptionStatus()
    }
    // SY <--

    inline fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = ArchiveInputStream(
        address,
        size,
        // SY -->
        encrypted,
        // SY <--
    ).use { block(generateSequence { it.getNextEntry() }) }

    fun getInputStream(entryName: String): InputStream? {
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

    override fun close() {
        Os.munmap(address, size)
    }
}

fun UniFile.archiveReader(context: Context) = openFileDescriptor(context, "r").use { ArchiveReader(it) }
