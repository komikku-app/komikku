package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class AzukiPageListDto(
    val data: AzukiPageDataDto,
)

@Serializable
data class AzukiPageDataDto(
    val pages: List<AzukiPageDto>,
)

@Serializable
data class AzukiPageDto(
    val image: Image,
)

@Serializable
data class Image(
    val webp: List<Webp>,
)

@Serializable
data class Webp(
    val url: String,
    val width: Int,
)
