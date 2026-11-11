package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

// KMK -->
/**
 * Writes the same bytes as encoding a whole [Backup], without holding all of its entries at once.
 */
class BackupWriter(
    private val sink: BufferedSink,
    private val parser: ProtoBuf,
) {
    var isEmpty = true
        private set

    fun writeEntry(entry: BackupManga) {
        val bytes = parser.encodeToByteArray(BackupManga.serializer(), entry)
        sink.writeVarint(ENTRY_TAG)
        sink.writeVarint(bytes.size.toLong())
        sink.write(bytes)
        isEmpty = false
    }

    /**
     * Writes the other fields after the entries, as [metadata] without entries of its own.
     */
    fun writeMetadata(metadata: Backup) {
        val bytes = parser.encodeToByteArray(Backup.serializer(), metadata)
        sink.write(bytes)
        if (bytes.isNotEmpty()) isEmpty = false
    }
}

/**
 * Reads an encoded [Backup] one entry at a time. Every other field is set aside for [readMetadata], wherever it
 * appears.
 *
 * Malformed or truncated input throws an [IOException].
 */
class BackupReader(
    private val source: BufferedSource,
    private val parser: ProtoBuf,
) {
    private val metadata = Buffer()
    private var entryLength = NO_ENTRY

    /**
     * Moves to the next entry, which [readEntry] or [skipEntry] then consumes.
     *
     * @return false once the input is exhausted.
     */
    fun nextEntry(): Boolean {
        check(entryLength == NO_ENTRY)
        while (!source.exhausted()) {
            val tag = source.readVarint()
            val fieldNumber = tag ushr 3
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 0L) throw IOException("Invalid field number 0")

            if (tag == ENTRY_TAG) {
                entryLength = source.readLength(MAX_ENTRY_BYTES)
                return true
            }

            val field = Buffer().apply { writeVarint(tag) }
            when (wireType) {
                WIRE_TYPE_VARINT -> field.writeVarint(source.readVarint())
                WIRE_TYPE_FIXED64 -> field.write(source, 8)
                WIRE_TYPE_LENGTH_DELIMITED -> {
                    val length = source.readLength(MAX_METADATA_FIELD_BYTES)
                    field.writeVarint(length)
                    field.write(source, length)
                }
                WIRE_TYPE_FIXED32 -> field.write(source, 4)
                else -> throw IOException("Unsupported wire type $wireType")
            }
            if (field.size > MAX_METADATA_BYTES - metadata.size) {
                throw IOException("Backup metadata exceeds the maximum size")
            }
            metadata.write(field, field.size)
        }
        return false
    }

    fun readEntry(): BackupManga = readEntry(BackupManga.serializer())

    /**
     * Decodes the entry as [deserializer], which can declare only the fields it needs so the rest are skipped.
     */
    fun <T> readEntry(deserializer: DeserializationStrategy<T>): T {
        val bytes = source.readByteArray(takeEntryLength())
        return decode(deserializer, bytes)
    }

    fun skipEntry() {
        source.skip(takeEntryLength())
    }

    /**
     * Skips the remaining entries and decodes everything else as a [Backup] without entries.
     */
    fun readMetadata(): Backup {
        if (entryLength != NO_ENTRY) skipEntry()
        while (nextEntry()) skipEntry()
        // Protobuf omits empty repeated fields, but this serializer requires the
        // backupManga list to be present when decoding a Backup.
        metadata.writeVarint(ENTRY_TAG)
        metadata.writeVarint(0)
        return decode(Backup.serializer(), metadata.readByteArray())
    }

    private fun takeEntryLength(): Long {
        check(entryLength != NO_ENTRY)
        return entryLength.also { entryLength = NO_ENTRY }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        return try {
            parser.decodeFromByteArray(deserializer, bytes)
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }
}

private fun BufferedSource.readVarint(): Long {
    var result = 0L
    for (shift in 0 until Long.SIZE_BITS step 7) {
        val byte = readByte().toInt()
        result = result or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) return result
    }
    throw IOException("Malformed varint")
}

private fun BufferedSource.readLength(maxBytes: Long): Long {
    val length = readVarint()
    if (length !in 0..maxBytes) throw IOException("Invalid length $length")
    return length
}

private fun BufferedSink.writeVarint(value: Long) {
    var remaining = value
    while (remaining and 0x7FL.inv() != 0L) {
        writeByte((remaining and 0x7F).toInt() or 0x80)
        remaining = remaining ushr 7
    }
    writeByte(remaining.toInt())
}

private const val WIRE_TYPE_VARINT = 0
private const val WIRE_TYPE_FIXED64 = 1
private const val WIRE_TYPE_LENGTH_DELIMITED = 2
private const val WIRE_TYPE_FIXED32 = 5

// Backup.backupManga is field 1, and each entry is length-delimited
private const val ENTRY_TAG = 1L shl 3 or WIRE_TYPE_LENGTH_DELIMITED.toLong()

private const val NO_ENTRY = -1L
private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
private const val MAX_METADATA_FIELD_BYTES = 8L * 1024 * 1024
private const val MAX_METADATA_BYTES = 32L * 1024 * 1024

// KMK <--
