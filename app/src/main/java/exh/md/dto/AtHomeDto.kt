package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class AtHomeDto(
    val baseUrl: String,
)

@Serializable
data class AtHomeImageReportDto(
    val url: String,
    val success: Boolean,
    val bytes: Int? = null,
    val cached: Boolean? = null,
    val duration: Long,
)
