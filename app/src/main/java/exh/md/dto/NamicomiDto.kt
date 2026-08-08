package exh.md.dto

import kotlinx.serialization.Serializable


@Serializable
data class NamicomiPageListDto(
    val result: String,
    val type: String,
    val data: NamicomiPageDataDto? = null,
)

@Serializable
data class NamicomiPageDataDto(
    val baseUrl: String,
    val hash: String,
    val low: List<NamicomiImageDto>,
    val source: List<NamicomiImageDto>,
)

@Serializable
data class NamicomiImageDto(
    val filename: String,
)
