package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMangaTaste(
    @ProtoNumber(1) val mangaId: Long = 0,
    @ProtoNumber(2) val source: Long = 0,
    @ProtoNumber(3) val url: String = "",
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val rating: Int = 0,
    @ProtoNumber(6) val updatedAt: Long = 0,
    @ProtoNumber(7) val createdAt: Long = 0,
)
