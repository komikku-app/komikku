package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ImageReportResult(
    val url: String,
    val success: Boolean,
    val bytes: Int?
)
