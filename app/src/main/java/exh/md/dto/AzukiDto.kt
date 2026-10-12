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
    val image: AzukiImage,
)

@Serializable
data class AzukiImage(
    val webp: List<AzukiWebp>,
)

@Serializable
data class AzukiWebp(
    val url: String,
    val width: Int,
)
