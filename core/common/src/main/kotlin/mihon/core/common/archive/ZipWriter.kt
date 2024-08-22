package mihon.core.common.archive

import android.content.Context
import android.system.Os
import android.system.StructStat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveEntry.AE_IFREG
import me.zhanghai.android.libarchive.ArchiveException
import tachiyomi.core.common.storage.openFileDescriptor
import java.io.Closeable
import java.nio.ByteBuffer

class ZipWriter(
    val context: Context,
    file: UniFile,
    // SY -->
    encrypt: Boolean = false,
    // SY <--
) : Closeable {
    private val pfd = file.openFileDescriptor(context, "wt")
    private val archive = Archive.writeNew()
    private val entry = ArchiveEntry.new2(archive)
    private val buffer = ByteBuffer.allocateDirect(
        // SY -->
        BUFFER_SIZE,
        // SY <--
    )

    init {
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.writeSetFormatZip(archive)
            Archive.writeZipSetCompressionStore(archive)
            // SY -->
            if (encrypt) {
                Archive.writeSetOptions(archive, CbzCrypto.getPreferredEncryptionAlgo())
                Archive.writeSetPassphrase(archive, CbzCrypto.getDecryptedPasswordCbz())
            }
            // SY <--
            Archive.writeOpenFd(archive, pfd.fd)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    fun write(file: UniFile) {
        file.openFileDescriptor(context, "r").use {
            val fd = it.fileDescriptor
            ArchiveEntry.clear(entry)
            ArchiveEntry.setPathnameUtf8(entry, file.name)
            val stat = Os.fstat(fd)
            ArchiveEntry.setStat(entry, stat.toArchiveStat())
            Archive.writeHeader(archive, entry)
            while (true) {
                buffer.clear()
                Os.read(fd, buffer)
                if (buffer.position() == 0) break
                buffer.flip()
                Archive.writeData(archive, buffer)
            }
            Archive.writeFinishEntry(archive)
        }
    }

    // SY -->
    fun write(fileData: ByteArray, fileName: String) {
        ArchiveEntry.clear(entry)
        ArchiveEntry.setPathnameUtf8(entry, fileName)
        ArchiveEntry.setSize(entry, fileData.size.toLong())
        ArchiveEntry.setFiletype(entry, AE_IFREG)
        Archive.writeHeader(archive, entry)

        var position = 0
        while (position < fileData.size) {
            val lengthToRead = minOf(BUFFER_SIZE, fileData.size - position)
            buffer.clear()
            buffer.put(fileData, position, lengthToRead)
            buffer.flip()
            Archive.writeData(archive, buffer)
            position += lengthToRead
        }
        Archive.writeFinishEntry(archive)
    }
    // SY <--

    override fun close() {
        ArchiveEntry.free(entry)
        Archive.writeFree(archive)
        pfd.close()
    }

    // SY -->
    companion object {
        private const val BUFFER_SIZE = 8192
    }
    // SY <--
}

private fun StructStat.toArchiveStat() = ArchiveEntry.StructStat().apply {
    stDev = st_dev
    stMode = st_mode
    stNlink = st_nlink.toInt()
    stUid = st_uid
    stGid = st_gid
    stRdev = st_rdev
    stSize = st_size
    stBlksize = st_blksize
    stBlocks = st_blocks
    stAtim = timespec(st_atime)
    stMtim = timespec(st_mtime)
    stCtim = timespec(st_ctime)
    stIno = st_ino
}

private fun timespec(tvSec: Long) = ArchiveEntry.StructTimespec().also { it.tvSec = tvSec }
