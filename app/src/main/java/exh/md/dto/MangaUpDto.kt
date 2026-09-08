package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MangaUpViewerResponse(
    @ProtoNumber(3) val pageBlocks: List<MangaUpPageBlock>,
)

@Serializable
class MangaUpPageBlock(
    @ProtoNumber(3) val pages: List<MangaUpMangaPage>,
)

@Serializable
class MangaUpMangaPage(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(5) val key: String?,
    @ProtoNumber(6) val iv: String?,
)
