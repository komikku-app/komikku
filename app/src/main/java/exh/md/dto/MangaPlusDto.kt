package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MangaPlusResponse(
    @ProtoNumber(1) val success: SuccessResult? = null,
)

@Serializable
data class SuccessResult(
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
)

@Serializable
data class MangaViewer(@ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList())

@Serializable
data class MangaPlusPage(@ProtoNumber(1) val page: MangaPage? = null)

@Serializable
data class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(2) val width: Int,
    @ProtoNumber(3) val height: Int,
    @ProtoNumber(5) val encryptionKey: String? = null,
)
