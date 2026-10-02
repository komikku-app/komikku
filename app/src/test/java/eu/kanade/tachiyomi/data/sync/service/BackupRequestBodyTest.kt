package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupRequestBodyTest {

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun `streams a backup that decodes back to the same data`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(source = 1L, url = "/manga/a", title = "A"),
                BackupManga(source = 2L, url = "/manga/b", title = "B", notes = "note"),
            ),
            backupCategories = listOf(BackupCategory(name = "Cat", order = 1L)),
        )

        val decoded = writeAndDecode(backup)

        assertEquals(backup.backupManga.map { it.comparable() }, decoded.backupManga.map { it.comparable() })
        assertEquals(
            backup.backupCategories.map { it.name to it.order },
            decoded.backupCategories.map { it.name to it.order },
        )
    }

    @Test
    fun `concatenates one record per manga in order`() {
        val backup = Backup(
            backupManga = (1L..25L).map {
                BackupManga(source = 1L, url = "/manga/$it", title = "Manga $it")
            },
        )

        val decoded = writeAndDecode(backup)

        assertEquals(backup.backupManga.map { it.url }, decoded.backupManga.map { it.url })
    }

    @Test
    fun `is byte-for-byte identical to encoding the whole backup`() {
        val backup = Backup(
            backupManga = (1L..5L).map {
                BackupManga(source = 1L, url = "/manga/$it", title = "Manga $it")
            },
            backupCategories = listOf(BackupCategory(name = "Cat", order = 1L)),
        )

        val expected = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        val buffer = Buffer()
        buildBody(backup).writeTo(buffer)

        assertArrayEquals(expected, buffer.readByteArray())
    }

    private fun writeAndDecode(backup: Backup): Backup {
        val buffer = Buffer()
        buildBody(backup).writeTo(buffer)
        return protoBuf.decodeFromByteArray(Backup.serializer(), buffer.readByteArray())
    }

    private fun BackupManga.comparable() = Triple(url, title, notes)

    private fun buildBody(backup: Backup): BackupRequestBody {
        val metaBytes = protoBuf.encodeToByteArray(
            Backup.serializer(),
            backup.copy(backupManga = emptyList()),
        )
        return BackupRequestBody(
            protoBuf = protoBuf,
            manga = backup.backupManga,
            metaBytes = metaBytes,
            contentType = "application/octet-stream".toMediaType(),
        )
    }
}
