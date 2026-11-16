package mihon.core.archive

import java.io.Closeable
import java.io.InputStream

/**
 * KMK: reader for comic archives (zip / cbz / epub, rar / cbr, 7z / cb7, tar, …), also used by
 * [EpubReader] and [CbzCrypto]. Each platform has its own constructor:
 *
 * - Android (`src/androidMain`, the upstream implementation): `ArchiveReader(ParcelFileDescriptor)`
 *   on libarchive over a memory-mapped file.
 * - Desktop (`src/desktopMain`): `ArchiveReader(File)` on zip4j / junrar / commons-compress.
 *
 * Password-protected archives are opened with the CBZ password from [CbzCrypto].
 */
expect class ArchiveReader : Closeable {

    /** Whether the archive has encrypted entries (it is then read with the CBZ password). */
    var encrypted: Boolean
        private set

    /** For an [encrypted] archive, whether the stored CBZ password failed; `null` otherwise. */
    var wrongPassword: Boolean?
        private set

    /** Identifies this open archive (temporary directories of the reader use it). */
    val archiveHashCode: Int

    /** Runs [block] over the archive's entries in archive order; the sequence is valid inside [block] only. */
    inline fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T

    /** Opens the entry named [entryName], or returns `null` if there is none. */
    fun getInputStream(entryName: String): InputStream?

    override fun close()
}
