package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class BackupMangaTasteTest {
    @Test
    fun `round trip preserves rating identity and timestamps`() {
        val expected = BackupMangaTaste(
            mangaId = 7,
            source = 10,
            url = "/series/example",
            title = "Example",
            rating = 2,
            updatedAt = 200,
            createdAt = 100,
        )

        val bytes = ProtoBuf.encodeToByteArray(BackupMangaTaste.serializer(), expected)

        ProtoBuf.decodeFromByteArray(BackupMangaTaste.serializer(), bytes) shouldBe expected
    }

    @Test
    fun `older rating backup decodes with default creation time`() {
        val legacy = LegacyBackupMangaTaste(
            mangaId = 7,
            source = 10,
            url = "/series/example",
            title = "Example",
            rating = 2,
            updatedAt = 200,
        )
        val bytes = ProtoBuf.encodeToByteArray(LegacyBackupMangaTaste.serializer(), legacy)

        val restored = ProtoBuf.decodeFromByteArray(BackupMangaTaste.serializer(), bytes)

        restored.createdAt shouldBe 0
        restored.updatedAt shouldBe 200
    }

    @Serializable
    private data class LegacyBackupMangaTaste(
        @ProtoNumber(1) val mangaId: Long,
        @ProtoNumber(2) val source: Long,
        @ProtoNumber(3) val url: String,
        @ProtoNumber(4) val title: String,
        @ProtoNumber(5) val rating: Int,
        @ProtoNumber(6) val updatedAt: Long,
    )
}
