package mihon.core.archive

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * KMK: desktop `actual` of [ArchiveReader] over an archive [file] (see [DesktopArchiveCursor] for the
 * supported formats). Like Android, every [useEntries] / [getInputStream] call walks the archive
 * with its own [ArchiveInputStream], so concurrent readers do not interfere.
 */
actual class ArchiveReader(val file: File) : Closeable {

    actual var encrypted: Boolean = false
        private set
    actual var wrongPassword: Boolean? = null
        private set
    actual val archiveHashCode: Int = System.identityHashCode(this)

    init {
        checkEncryptionStatus()
    }

    actual inline fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = ArchiveInputStream(
        file,
        encrypted,
    ).use { block(generateSequence { it.getNextEntry() }) }

    actual fun getInputStream(entryName: String): InputStream? {
        val archive = ArchiveInputStream(file, encrypted)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    return archive
                }
            }
        } catch (e: Exception) {
            archive.close()
            throw e
        }
        archive.close()
        return null
    }

    private fun checkEncryptionStatus() {
        val encryptedEntry = ArchiveInputStream(file, false).use { archive ->
            generateSequence { archive.getNextEntry() }.firstOrNull { it.isEncrypted }
        } ?: return
        encrypted = true
        wrongPassword = isPasswordIncorrect(encryptedEntry.name)
    }

    private fun isPasswordIncorrect(entryName: String): Boolean {
        return try {
            getInputStream(entryName).use { stream -> stream!!.read() }
            false
        } catch (e: IOException) {
            if (!e.isWrongPassword()) throw e
            true
        }
    }

    actual override fun close() {
        // Nothing stays open between calls: each read opens its own ArchiveInputStream.
    }
}
