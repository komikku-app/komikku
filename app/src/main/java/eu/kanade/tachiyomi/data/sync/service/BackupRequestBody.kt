package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * A streaming [RequestBody] for a protobuf-encoded backup.
 *
 * Serialising the whole backup with `ProtoBuf.encodeToByteArray` needs a single contiguous
 * buffer that kotlinx.serialization grows by doubling and copying, so a large library peaks
 * at roughly twice its serialized size and can throw [OutOfMemoryError] on devices with a
 * small heap even though the library itself fits in memory.
 *
 * This body instead writes one length-delimited `backupManga` (field 1) record per manga
 * followed by the backup's other fields ([metaBytes]). Concatenating a repeated protobuf
 * field's records is valid (successive messages of the same type merge), so the result
 * decodes to the same [eu.kanade.tachiyomi.data.backup.models.Backup] while peak memory is
 * bounded by the largest single manga instead of the whole library. The payload has no
 * known length, so it is sent with chunked transfer-encoding.
 */
class BackupRequestBody(
    private val protoBuf: ProtoBuf,
    private val manga: Iterable<BackupManga>,
    private val metaBytes: ByteArray,
    private val contentType: MediaType?,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    // Unknown length: OkHttp falls back to chunked transfer-encoding.
    override fun contentLength(): Long = -1L

    override fun writeTo(sink: BufferedSink) {
        // The manga records go first so the streamed payload is byte-for-byte identical to
        // ProtoBuf.encodeToByteArray(Backup.serializer(), backup): Backup declares
        // backupManga (field 1) before its other fields.
        val serializer = MangaChunk.serializer()
        for (entry in manga) {
            sink.write(protoBuf.encodeToByteArray(serializer, MangaChunk(entry)))
        }

        sink.write(metaBytes)
    }

    /**
     * Emits a single `backupManga` field record (field 1, wire type 2). Its encoding is
     * identical to one element of
     * [eu.kanade.tachiyomi.data.backup.models.Backup.backupManga], which is what makes the
     * concatenated stream a valid Backup message.
     */
    @Serializable
    private class MangaChunk(
        @ProtoNumber(1) val manga: BackupManga,
    )
}
