package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupSource = when (id1id2.toInt()) {
                0x1f8b -> source.gzip() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }
            val backupString = limited(backupSource).use { it.readByteArray() }

            try {
                parser.decodeFromByteArray(Backup.serializer(), backupString)
            } catch (_: SerializationException) {
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }
        }
    }

    // KMK -->
    /**
     * Reads backup metadata while skipping manga entries, keeping the entry count for restore progress.
     */
    fun decodeMetadata(uri: Uri): Metadata = withReader(uri) { reader ->
        var entryCount = 0
        while (reader.nextEntry()) {
            reader.skipEntry()
            entryCount++
        }
        Metadata(entryCount, reader.readMetadata())
    }

    /**
     * Decodes entries with a smaller deserializer during the metadata scan.
     */
    fun <T> decodeMetadata(
        uri: Uri,
        entry: DeserializationStrategy<T>,
        onEntry: (T) -> Unit,
    ): Metadata = withReader(uri) { reader ->
        var entryCount = 0
        while (reader.nextEntry()) {
            onEntry(reader.readEntry(entry))
            entryCount++
        }
        Metadata(entryCount, reader.readMetadata())
    }

    /**
     * Streams backup manga entries one at a time.
     */
    fun decodeEntries(uri: Uri): Flow<BackupManga> = flow {
        withReader(uri) { reader ->
            while (reader.nextEntry()) emit(reader.readEntry())
        }
    }

    data class Metadata(val entryCount: Int, val backup: Backup)

    private inline fun <T> withReader(uri: Uri, block: (BackupReader) -> T): T {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()
            val peeked = source.peek().apply { require(2) }
            val id1id2 = peeked.readShort()
            val backupSource: BufferedSource = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer()
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }
            limited(backupSource).use { block(BackupReader(it, parser)) }
        }
    }

    private fun limited(source: Source): BufferedSource =
        object : ForwardingSource(source) {
            private var bytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0L
                val remaining = MAX_DECOMPRESSED_BACKUP_BYTES - bytesRead
                val read = super.read(sink, minOf(byteCount, remaining + 1))
                if (read > remaining) throw IOException("Backup exceeds the maximum decompressed size")
                if (read > 0) bytesRead += read
                return read
            }
        }.buffer()
    // KMK <--

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}

// KMK -->
private const val MAX_DECOMPRESSED_BACKUP_BYTES = 128L * 1024 * 1024
// KMK <--
